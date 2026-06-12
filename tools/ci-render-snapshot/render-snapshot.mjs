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
const requestedGlMode = arg('gl-mode', process.env.PHASE26_GL_MODE ?? 'auto');

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
    if (typeof window.s57Phase26ReportJson === 'string') return JSON.parse(window.s57Phase26ReportJson || '{}');
    throw new Error('window.s57Phase26LatestReportJson is not available');
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

async function pageWebGl2Info(page) {
  return await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 4;
    canvas.height = 4;
    const gl = canvas.getContext('webgl2', {
      alpha: false,
      antialias: false,
      depth: false,
      stencil: false,
      failIfMajorPerformanceCaveat: false
    });
    if (!gl) return { available: false, renderer: '', vendor: '', version: '' };
    const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
    return {
      available: true,
      renderer: debugInfo ? String(gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL)) : '',
      vendor: debugInfo ? String(gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL)) : '',
      version: String(gl.getParameter(gl.VERSION) || '')
    };
  });
}

function chromiumLaunchCandidates() {
  const common = [
    '--enable-webgl',
    '--enable-gpu',
    '--ignore-gpu-blocklist',
    '--disable-gpu-sandbox',
    '--disable-dev-shm-usage',
    '--allow-unsafe-swiftshader',
    '--enable-unsafe-swiftshader'
  ];
  const candidates = [];
  const add = (name, extraArgs) => candidates.push({ name, args: [...common, ...extraArgs] });

  // First prefer regular headed Chromium/Xvfb with its default GL stack.  On
  // GitHub runners this often resolves to Mesa/llvmpipe and exposes WebGL2.
  // Forcing ANGLE/SwiftShader too early can disable WebGL2 on some Chromium
  // builds, which was the source of the blue-only render.png regression.
  if (requestedGlMode === 'auto' || requestedGlMode === 'native') add('native-default-gl', []);
  if (requestedGlMode === 'auto' || requestedGlMode === 'egl') add('egl', ['--use-gl=egl']);
  if (requestedGlMode === 'auto' || requestedGlMode === 'desktop') add('desktop-gl', ['--use-gl=desktop']);
  if (requestedGlMode === 'auto' || requestedGlMode === 'swiftshader') add('swiftshader-gl', ['--use-gl=swiftshader']);
  if (requestedGlMode === 'auto' || requestedGlMode === 'angle-swiftshader') add('angle-swiftshader', ['--use-gl=angle', '--use-angle=swiftshader']);
  return candidates;
}

async function launchBrowserWithWebGl2Probe() {
  const candidates = chromiumLaunchCandidates();
  const failures = [];
  for (const candidate of candidates) {
    const launchOptions = { headless, args: candidate.args, ignoreDefaultArgs: ['--disable-gpu'] };
    if (browserChannel && browserChannel !== 'default') launchOptions.channel = browserChannel;
    console.log(`Phase 26 probing Chromium headless=${headless} channel=${browserChannel || 'default'} glMode=${candidate.name}`);
    let candidateBrowser;
    try {
      candidateBrowser = await chromium.launch(launchOptions);
      const probePage = await candidateBrowser.newPage({ viewport: { width: 16, height: 16 } });
      const info = await pageWebGl2Info(probePage);
      await probePage.close().catch(() => {});
      if (info.available) {
        console.log(`Phase 26 selected Chromium glMode=${candidate.name} webgl2=${info.version} renderer=${info.renderer || 'unknown'}`);
        return { browser: candidateBrowser, glMode: candidate.name, webGl2Info: info };
      }
      failures.push(`${candidate.name}: WebGL2 unavailable`);
    } catch (error) {
      failures.push(`${candidate.name}: ${error?.message ?? String(error)}`);
    }
    if (candidateBrowser) await candidateBrowser.close().catch(() => {});
  }
  throw new Error(
    `Chromium did not expose WebGL2 before loading the chart app (channel=${browserChannel || 'default'}, headless=${headless}, requestedGlMode=${requestedGlMode}). ` +
    `Tried: ${failures.join('; ')}. ` +
    'Use a runner/browser with WebGL2, or set PHASE26_GL_MODE=native|egl|desktop|swiftshader|angle-swiftshader to force a specific backend.'
  );
}

const { server, url } = await startServer(appDir);
let browser;
try {
  const launched = await launchBrowserWithWebGl2Probe();
  browser = launched.browser;
  const selectedGlMode = launched.glMode;
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
      `but the chart canvas lost WebGL2 after the preflight succeeded (channel=${browserChannel || 'default'}, headless=${headless}, glMode=${selectedGlMode}). ` +
      'Refusing to publish a blank blue render.png. This usually means some earlier code claimed the canvas with WebGL1/2D before S-52 WebGL2 rendering.'
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
    `chromiumGlMode=${selectedGlMode}`,
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
