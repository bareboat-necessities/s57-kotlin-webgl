#!/usr/bin/env node
import { chromium } from 'playwright';
import { createServer } from 'node:http';
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { inflateSync } from 'node:zlib';
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
const headless = arg('headless', process.env.PHASE26_HEADLESS ?? 'true') !== 'false';
const browserChannel = arg('browser-channel', process.env.PHASE26_BROWSER_CHANNEL ?? 'chromium');

if (!existsSync(appDir)) throw new Error(`Demo app directory does not exist: ${appDir}`);
if (!existsSync(encFile) || statSync(encFile).size <= 0) throw new Error(`ENC file is missing or empty: ${encFile}`);
mkdirSync(outDir, { recursive: true });
rmSync(path.join(outDir, 'render.png'), { force: true });

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

function pngVisualStats(file) {
  const data = readFileSync(file);
  const signature = '89504e470d0a1a0a';
  if (data.subarray(0, 8).toString('hex') !== signature) {
    throw new Error(`Not a PNG file: ${file}`);
  }
  let offset = 8;
  let width = 0;
  let height = 0;
  let bitDepth = 0;
  let colorType = 0;
  const idatChunks = [];
  while (offset + 12 <= data.length) {
    const length = data.readUInt32BE(offset);
    const type = data.subarray(offset + 4, offset + 8).toString('ascii');
    const chunk = data.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = chunk.readUInt32BE(0);
      height = chunk.readUInt32BE(4);
      bitDepth = chunk[8];
      colorType = chunk[9];
    } else if (type === 'IDAT') {
      idatChunks.push(chunk);
    } else if (type === 'IEND') {
      break;
    }
    offset += 12 + length;
  }
  if (width <= 0 || height <= 0 || idatChunks.length <= 0) {
    throw new Error(`PNG is missing IHDR/IDAT: ${file}`);
  }
  if (bitDepth !== 8 || ![2, 6].includes(colorType)) {
    throw new Error(`Unsupported PNG format for visual stats: bitDepth=${bitDepth} colorType=${colorType}`);
  }
  const channels = colorType === 6 ? 4 : 3;
  const stride = width * channels;
  const inflated = inflateSync(Buffer.concat(idatChunks));
  const previous = Buffer.alloc(stride);
  const current = Buffer.alloc(stride);
  const counts = new Map();
  let sourceOffset = 0;
  let samples = 0;
  const sampleXStep = Math.max(1, Math.floor(width / 128));
  const sampleYStep = Math.max(1, Math.floor(height / 72));
  for (let y = 0; y < height; y += 1) {
    const filter = inflated[sourceOffset++];
    const row = inflated.subarray(sourceOffset, sourceOffset + stride);
    sourceOffset += stride;
    for (let x = 0; x < stride; x += 1) {
      const left = x >= channels ? current[x - channels] : 0;
      const up = previous[x];
      const upLeft = x >= channels ? previous[x - channels] : 0;
      let value = row[x];
      if (filter === 1) value = (value + left) & 255;
      else if (filter === 2) value = (value + up) & 255;
      else if (filter === 3) value = (value + Math.floor((left + up) / 2)) & 255;
      else if (filter === 4) {
        const p = left + up - upLeft;
        const pa = Math.abs(p - left);
        const pb = Math.abs(p - up);
        const pc = Math.abs(p - upLeft);
        const predictor = pa <= pb && pa <= pc ? left : (pb <= pc ? up : upLeft);
        value = (value + predictor) & 255;
      } else if (filter !== 0) {
        throw new Error(`Unsupported PNG row filter ${filter}`);
      }
      current[x] = value;
    }
    if (y % sampleYStep === 0) {
      for (let x = 0; x < width; x += sampleXStep) {
        const i = x * channels;
        const r = current[i] >> 3;
        const g = current[i + 1] >> 3;
        const b = current[i + 2] >> 3;
        const a = channels === 4 ? (current[i + 3] >> 5) : 7;
        const key = `${r},${g},${b},${a}`;
        counts.set(key, (counts.get(key) ?? 0) + 1);
        samples += 1;
      }
    }
    previous.set(current);
  }
  const dominant = counts.size > 0 ? Math.max(...counts.values()) : 0;
  return {
    width,
    height,
    samples,
    distinctColors: counts.size,
    dominantRatio: samples > 0 ? dominant / samples : 1
  };
}



function readThresholds() {
  return existsSync(thresholdsFile) ? JSON.parse(readFileSync(thresholdsFile, 'utf8')) : {};
}

function thresholdWarnings(report) {
  const thresholds = readThresholds();
  const limits = thresholds.warningOnlyLimits ?? {};
  const warnings = [];
  for (const [name, max] of Object.entries(limits)) {
    const actual = reportCounter(report, name);
    if (actual > Number(max)) warnings.push(`${name}=${actual} exceeds warning-only baseline ${max}`);
  }
  return warnings;
}

function thresholdFailures(report, options = {}) {
  const thresholds = readThresholds();
  const minimums = thresholds.minimumCounters ?? {};
  const forbiddenCodes = thresholds.forbiddenCodes ?? [];
  const ignoredForbiddenCodes = new Set(options.ignoredForbiddenCodes ?? []);
  const failures = [];
  for (const [name, min] of Object.entries(minimums)) {
    const actual = reportCounter(report, name);
    if (actual < Number(min)) failures.push(`${name}=${actual} is below required minimum ${min}`);
  }
  for (const code of forbiddenCodes) {
    if (ignoredForbiddenCodes.has(code)) continue;
    const actual = Number(report?.countsByCode?.[code] ?? 0) || 0;
    if (actual > 0) failures.push(`${code} occurred ${actual} time(s)`);
  }
  return failures;
}

async function readPhase26Report(page) {
  return await page.evaluate(() => {
    // Prefer the JSON export.  It is stable across Kotlin/JS dynamic interop
    // changes, while the object-returning helper is only a convenience for
    // manual browser debugging.
    if (typeof window.s57Phase26LatestReportJson === 'string') return JSON.parse(window.s57Phase26LatestReportJson || '{}');
    if (typeof window.s57Phase26ReportJson === 'function') return JSON.parse(window.s57Phase26ReportJson());
    if (typeof window.s57Phase26ReportJson === 'string') return JSON.parse(window.s57Phase26ReportJson);
    if (typeof window.s57Phase26Report === 'function') return window.s57Phase26Report();
    throw new Error('window.s57Phase26ReportJson is not available');
  });
}

function hasWebGl2EnvironmentDiagnostic(report) {
  return Boolean(report?.diagnostics?.some((diagnostic) => {
    const message = String(diagnostic?.message ?? '');
    return diagnostic?.code === 's52.debug_geometry_fallback_suppressed' &&
      message.includes('WebGL2 is not available');
  }));
}

async function chartCanvasWebGl2Available(page) {
  return await page.evaluate(() => {
    const canvas = document.querySelector('#chartCanvas');
    if (!canvas) return false;
    try {
      return Boolean(canvas.getContext('webgl2'));
    } catch (_) {
      return false;
    }
  });
}

const { server, url } = await startServer(appDir);
let browser;
try {
  const launchOptions = {
    headless,
    args: [
      '--enable-webgl',
      '--ignore-gpu-blocklist',
      '--disable-gpu-sandbox',
      '--disable-dev-shm-usage',
      '--enable-unsafe-swiftshader',
      '--use-angle=swiftshader',
      '--use-gl=angle'
    ]
  };
  if (browserChannel && browserChannel !== 'default') {
    launchOptions.channel = browserChannel;
  }
  console.log(`Phase 26 launching Chromium headless=${headless} channel=${browserChannel || 'default'}`);
  browser = await chromium.launch(launchOptions);
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
      // Trigger a real DOM click instead of calling a Kotlin function object
      // from JavaScript.  The DOM event path avoids Kotlin/JS dynamic bridge
      // failures such as "P.asDynamic is not a function".
      document.querySelector('#renderButton')?.click();
    });
  }
  try {
    await page.waitForFunction(() => Boolean(window.s57Phase26RenderReady), null, { timeout: 90000 });
  } catch (error) {
    const statusText = await page.locator('#status').textContent().catch(() => 'status element unavailable');
    const cellText = await page.locator('#cellSummary').textContent().catch(() => 'cell summary unavailable');
    throw new Error(`Timed out waiting for Phase 26 render readiness. status=${statusText} cellSummary=${cellText}`);
  }
  let report = await readPhase26Report(page);
  const hasS52Commands = reportCounter(report, 's52Commands') > 0;
  if (!hasS52Commands) {
    writeFileSync(path.join(outDir, 'diagnostics.json'), `${JSON.stringify(report, null, 2)}\n`);
    throw new Error('Phase 26 did not produce any S-52 commands; refusing to write a blank render.png');
  }

  const webGl2Available = await chartCanvasWebGl2Available(page);
  const webGl2EnvironmentFailure = hasWebGl2EnvironmentDiagnostic(report) || !webGl2Available;
  if (webGl2EnvironmentFailure) {
    writeFileSync(path.join(outDir, 'diagnostics.json'), `${JSON.stringify(report, null, 2)}\n`);
    throw new Error(
      `Phase 26 S-52 portrayal produced ${reportCounter(report, 's52Commands')} commands, ` +
      `but the browser did not expose a usable WebGL2 renderer (channel=${browserChannel || 'default'}, headless=${headless}). ` +
      'Refusing to publish a blank blue render.png. Install regular Chromium with `npx playwright install --with-deps --no-shell chromium` and run with `--browser-channel=chromium` under Xvfb.'
    );
  }

  let drawCalls = reportCounter(report, 's52DrawCalls');
  if (drawCalls <= 0) {
    await page.waitForTimeout(500);
    report = await readPhase26Report(page);
    drawCalls = reportCounter(report, 's52DrawCalls');
  }
  if (drawCalls <= 0) {
    writeFileSync(path.join(outDir, 'diagnostics.json'), `${JSON.stringify(report, null, 2)}\n`);
    throw new Error(`Phase 26 S-52 produced ${reportCounter(report, 's52Commands')} commands but zero draw calls; refusing to write a blank render.png`);
  }

  await page.waitForTimeout(250);


  const failures = thresholdFailures(report);
  if (failures.length > 0) {
    writeFileSync(path.join(outDir, 'diagnostics.json'), `${JSON.stringify(report, null, 2)}\n`);
    throw new Error(`Phase 26 hard render thresholds failed: ${failures.join('; ')}`);
  }

  const canvas = page.locator('#chartCanvas');
  const renderPath = path.join(outDir, 'render.png');
  await canvas.screenshot({ path: renderPath });
  if (!existsSync(renderPath) || statSync(renderPath).size <= 0) {
    throw new Error('Canvas PNG snapshot is empty');
  }
  const visualStats = pngVisualStats(renderPath);
  if (visualStats.distinctColors <= 2 || visualStats.dominantRatio >= 0.995) {
    rmSync(renderPath, { force: true });
    writeFileSync(path.join(outDir, 'diagnostics.json'), `${JSON.stringify(report, null, 2)}\n`);
    throw new Error(
      `Phase 26 render.png appears blank: distinctColors=${visualStats.distinctColors} ` +
      `dominantRatio=${visualStats.dominantRatio.toFixed(6)}. Refusing to publish a blue-water-only snapshot.`
    );
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
    `webGl2Available=${webGl2Available}`,
    `canvasDistinctColors=${visualStats.distinctColors}`,
    `canvasDominantRatio=${visualStats.dominantRatio.toFixed(6)}`,
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
