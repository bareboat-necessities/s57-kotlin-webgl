#!/usr/bin/env node
import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { createReadStream, existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

function arg(name, fallback) {
  const prefix = `--${name}=`;
  const match = process.argv.find((value) => value.startsWith(prefix));
  return match ? match.slice(prefix.length) : fallback;
}

const appDir = path.resolve(rootDir, arg('app-dir', 'demo/build/dist/js/productionExecutable'));
const encFile = path.resolve(rootDir, arg('enc-file', 'build/ci-enc-snapshot/input/cell.000'));
const outDir = path.resolve(rootDir, arg('out-dir', 'build/ci-enc-snapshot'));
const thresholdsFile = path.resolve(rootDir, arg('thresholds', 'config/phase26-snapshot-thresholds.json'));
const width = Number(arg('width', '1280'));
const height = Number(arg('height', '720'));

if (!existsSync(appDir)) throw new Error(`Demo app directory does not exist: ${appDir}`);
if (!existsSync(encFile) || statSync(encFile).size <= 0) throw new Error(`ENC file is missing or empty: ${encFile}`);
mkdirSync(outDir, { recursive: true });

const mimeTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'application/javascript; charset=utf-8'],
  ['.wasm', 'application/wasm'],
  ['.json', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.css', 'text/css; charset=utf-8'],
  ['.000', 'application/octet-stream']
]);

function startServer(directory) {
  const server = createServer((request, response) => {
    const url = new URL(request.url ?? '/', 'http://127.0.0.1');
    const relative = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname);
    const resolved = path.resolve(directory, `.${relative}`);
    if (!resolved.startsWith(directory) || !existsSync(resolved) || !statSync(resolved).isFile()) {
      response.writeHead(404);
      response.end('not found');
      return;
    }
    response.writeHead(200, { 'content-type': mimeTypes.get(path.extname(resolved)) ?? 'application/octet-stream' });
    createReadStream(resolved).pipe(response);
  });
  return new Promise((resolve, reject) => {
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      resolve({ server, url: `http://127.0.0.1:${address.port}/` });
    });
  });
}

function readOptional(file) {
  return existsSync(file) ? readFileSync(file, 'utf8').trim() : '';
}

function reportCounter(report, name) {
  return Number(report?.counters?.[name] ?? report?.countsByCode?.[name] ?? 0) || 0;
}


async function assertS52OpenCpnAtlasPresent(page) {
  const result = await page.evaluate(async () => {
    const names = [
      's52/opencpn/rastersymbols-day.png',
      's52/opencpn/rastersymbols-dusk.png',
      's52/opencpn/rastersymbols-dark.png'
    ];
    const checks = [];
    for (const name of names) {
      try {
        const response = await fetch(name, { cache: 'no-store' });
        checks.push({ name, ok: response.ok, status: response.status });
      } catch (error) {
        checks.push({ name, ok: false, status: 0, message: String(error) });
      }
    }
    return checks;
  });
  const missing = result.filter((item) => !item.ok);
  if (missing.length > 0) {
    throw new Error(`S-52/OpenCPN raster atlas was not served by the browser app: ${missing.map((item) => `${item.name}=HTTP ${item.status}${item.message ? ` ${item.message}` : ''}`).join(', ')}`);
  }
}

function thresholdWarnings(report) {
  if (!existsSync(thresholdsFile)) return [];
  const thresholds = JSON.parse(readFileSync(thresholdsFile, 'utf8'));
  const limits = thresholds.warningOnlyLimits ?? {};
  const warnings = [];
  for (const [name, max] of Object.entries(limits)) {
    const actual = reportCounter(report, name);
    if (actual > Number(max)) warnings.push(`${name}=${actual} exceeds warning-only baseline ${max}`);
  }
  return warnings;
}

const { server, url } = await startServer(appDir);
let browser;
try {
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width, height } });
  page.on('console', (message) => console.log(`[browser:${message.type()}] ${message.text()}`));
  page.on('pageerror', (error) => console.error(`[browser:error] ${error.message}`));
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  await page.evaluate(() => { window.s57Phase26SnapshotMode = true; });
  await page.locator('#fileInput').setInputFiles(encFile);
  await page.waitForFunction(() => {
    const select = document.querySelector('#cellSelect');
    return Boolean(select && select.value);
  }, null, { timeout: 90000 });
  if (!(await page.evaluate(() => Boolean(window.s57Phase26RenderReady)))) {
    await page.evaluate(() => {
      if (typeof window.s57Phase26RenderSnapshot === 'function') window.s57Phase26RenderSnapshot();
      else document.querySelector('#renderButton')?.click();
    });
  }
  try {
    await page.waitForFunction(() => Boolean(window.s57Phase26RenderReady), null, { timeout: 90000 });
  } catch (error) {
    const statusText = await page.locator('#status').textContent().catch(() => 'status element unavailable');
    const cellText = await page.locator('#cellSummary').textContent().catch(() => 'cell summary unavailable');
    throw new Error(`Timed out waiting for Phase 26 render readiness. status=${statusText} cellSummary=${cellText}`);
  }
  const reportJson = await page.evaluate(() => {
    if (typeof window.s57Phase26ReportJson === 'function') return window.s57Phase26ReportJson();
    if (typeof window.s57Phase26Report === 'function') return JSON.stringify(window.s57Phase26Report());
    return '';
  });
  if (!reportJson) throw new Error('Phase 26 diagnostics JSON hook returned an empty value');
  const report = JSON.parse(reportJson);
  if (!report || report.schemaVersion == null || !Array.isArray(report.diagnostics)) {
    throw new Error('Malformed Phase 26 diagnostics JSON');
  }
  if (Number(report?.counters?.s52DrawCalls ?? 0) > 0) {
    await assertS52OpenCpnAtlasPresent(page);
    await page.waitForFunction(
      () => Boolean(window.s57S52ResourceRenderReady) || Number(window.s57S52ResourceRenderCount || 0) > 0,
      null,
      { timeout: 10000 }
    ).catch(() => undefined);
    await page.waitForTimeout(250);
  }
  const canvas = page.locator('#chartCanvas');
  await canvas.screenshot({ path: path.join(outDir, 'render.png') });
  if (!existsSync(path.join(outDir, 'render.png')) || statSync(path.join(outDir, 'render.png')).size <= 0) {
    throw new Error('Canvas PNG snapshot is empty');
  }
  writeFileSync(path.join(outDir, 'diagnostics.json'), `${JSON.stringify(report, null, 2)}\n`);
  const selectedUrl = readOptional(path.resolve(rootDir, 'build/ci-enc-snapshot/input/selected-url.txt'));
  const extractedEncFile = readOptional(path.resolve(rootDir, 'build/ci-enc-snapshot/input/extracted-enc-path.txt'));
  const warnings = thresholdWarnings(report);
  const summary = [
    'Phase 26 ENC render snapshot',
    `selectedUrl=${selectedUrl || 'unknown'}`,
    `extractedEncFile=${extractedEncFile || encFile}`,
    `encFile=${encFile}`,
    `cellId=${report.cellId ?? 'unknown'}`,
    `palette=${report.palette ?? 'unknown'}`,
    `scaleDenominator=${report.scaleDenominator ?? 'unknown'}`,
    `imageWidth=${width}`,
    `imageHeight=${height}`,
    `diagnostics=${report.diagnostics.length}`,
    `warnings=${report.countsBySeverity?.warning ?? 0}`,
    `errors=${report.countsBySeverity?.error ?? 0}`,
    `decodedFeatures=${report.counters?.decodedFeatures ?? 'unknown'}`,
    `indexedFeatures=${report.counters?.indexedFeatures ?? 'unknown'}`,
    `queriedFeatures=${report.counters?.queriedFeatures ?? 'unknown'}`,
    `adaptedFeatures=${report.counters?.adaptedFeatures ?? 'unknown'}`,
    `s52Commands=${report.counters?.s52Commands ?? 'unknown'}`,
    `s52DrawCalls=${report.counters?.s52DrawCalls ?? 'unknown'}`,
    `missingSymbols=${report.counters?.missingSymbols ?? 0}`,
    `missingColorTokens=${report.counters?.missingColorTokens ?? 0}`,
    `fallbackColors=${report.counters?.fallbackColors ?? 0}`,
    `fallbackPlaceholderCount=${report.counters?.fallbackPlaceholderCount ?? 0}`,
    ...warnings.map((warning) => `warningOnlyThreshold=${warning}`)
  ].join('\n');
  writeFileSync(path.join(outDir, 'summary.txt'), `${summary}\n`);
  console.log(summary);
} finally {
  if (browser) await browser.close();
  await new Promise((resolve) => server.close(resolve));
}
