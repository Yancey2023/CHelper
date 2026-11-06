// Run the shipped JS adapter and Wasm in V8 without HTTP or UI costs.
// Usage: node scripts/benchmark_web_core.mjs baseline.wasm current.wasm pack.cpack
import { readFileSync } from "node:fs";
import { performance, PerformanceObserver } from "node:perf_hooks";
import { createHash } from "node:crypto";
import { createInterface } from "node:readline";

const [baseline, current, packPath, repeatArgument] = process.argv.slice(2);
const iterations = Number(repeatArgument ?? 500);
const rounds = Number(process.env.CHELPER_WEB_BENCH_ROUNDS ?? 5);
if (!baseline || !current || !packPath)
  throw new Error("Expected baseline.wasm current.wasm pack.cpack");
if (
  !Number.isSafeInteger(iterations) ||
  iterations < 1 ||
  !Number.isSafeInteger(rounds) ||
  rounds < 1
)
  throw new Error("Benchmark iterations and rounds must be positive integers");

// The launcher controls only this benchmark process. Handshakes occur outside measurements.
const control =
  process.env.CHELPER_WEB_BENCH_CONTROL === "1"
    ? createInterface({ input: process.stdin })
    : null;
const controlLines = control?.[Symbol.asyncIterator]();
const policyObservations = [];
async function policyPhase(phase) {
  if (!control) return;
  console.error("CHelperBenchControl " + JSON.stringify({ phase }));
  const { value, done } = await controlLines.next();
  if (done) throw new Error("Benchmark controller closed before " + phase);
  const observation = JSON.parse(value);
  policyObservations.push(observation);
  if (observation.phase !== phase || !observation.ok)
    throw new Error(
      "Benchmark policy handshake failed: " + JSON.stringify(observation),
    );
}

const gc = [];
const observer = new PerformanceObserver((list) => {
  for (const entry of list.getEntries())
    gc.push({
      start: entry.startTime,
      duration: entry.duration,
      detail: entry.detail,
    });
});
observer.observe({ entryTypes: ["gc"] });
const pack = readFileSync(packPath);
const glue = readFileSync(
  new URL("../CHelper-Web/src/core/libCHelperWeb.js", import.meta.url),
  "utf8",
).replace(/^import wasmUrl[^\n]*\n/, "");
const commands = [
  "list",
  "give @s stone",
  "give @s ",
  "give ",
  'give @s command_block 12 12 {"minecraft:can_destroy":{"blocks":["minecraft:acacia_door"]}}',
  "execute as @a at @s if block ~~~ stone run setblock ~~~ air",
  "tag @e[type=zombie,r=16] add test",
  'tellraw @a {"rawtext":[{"text":"hello 世界"}]}',
  "give @s s",
  "setblock ~~~ candle_cake[lit=",
  "not_a_command",
];

const compiledModules = new Map();
async function load(path, label) {
  console.error(`Compiling ${path}`);
  const bytes = readFileSync(path);
  const sha256 = createHash("sha256").update(bytes).digest("hex");
  if (!compiledModules.has(sha256))
    compiledModules.set(sha256, new WebAssembly.Module(bytes));
  const compiled = compiledModules.get(sha256);
  console.error(`Instantiating ${path}`);
  // Use a real ES module in the same realm: vm context proxies otherwise
  // dominate the adapter's many typed-array reads and distort its timings.
  globalThis.__chelperBenchModules ??= new Map();
  globalThis.__chelperBenchModules.set(label, {
    instantiateWasm: (imports, receive) =>
      receive(new WebAssembly.Instance(compiled, imports)),
  });
  const source =
    glue.replace(
      'var Module=typeof Module!="undefined"?Module:{};',
      `var Module=globalThis.__chelperBenchModules.get(${JSON.stringify(label)});`,
    ) +
    "\nexport function linearMemoryBytes() { return wasmMemory.buffer.byteLength }\n";
  const adapter = await import(
    `data:text/javascript;base64,${Buffer.from(source).toString("base64")}`
  );
  await adapter.createWasmFuture;
  const nativeSuggestionSize =
    globalThis.__chelperBenchModules.get(label)._contextGetSuggestionSize;
  globalThis.__chelperBenchModules.delete(label);
  console.error(`Initialized ${path}`);
  return {
    Core: adapter.CHelperCore,
    linearMemoryBytes: adapter.linearMemoryBytes,
    nativeSuggestionSize,
    wasmSha256: sha256,
  };
}

let variants;
const records = [];
const fingerprints = {};
let checksum = 0;
let failure = null;
function measure(label, name, body, repeat = iterations) {
  for (let i = 0; i < 30; ++i) body();
  const usageBefore = process.resourceUsage();
  const start = performance.now();
  for (let i = 0; i < repeat; ++i) body();
  const end = performance.now();
  const usageAfter = process.resourceUsage();
  return {
    variant: label,
    name,
    us: ((end - start) * 1000) / repeat,
    start,
    end,
    repeat,
    pageFaults:
      usageAfter.majorPageFault +
      usageAfter.minorPageFault -
      usageBefore.majorPageFault -
      usageBefore.minorPageFault,
  };
}

try {
  await policyPhase("begin");
  variants = {
    baseline: await load(baseline, "baseline"),
    current: await load(current, "current"),
  };
  console.error("Wasm modules initialized");
  for (let round = 0; round < rounds; ++round) {
    for (const label of round % 2
      ? ["current", "baseline"]
      : ["baseline", "current"]) {
      console.error(`Starting ${label} round ${round}`);
      const { Core, linearMemoryBytes, nativeSuggestionSize } = variants[label];
      records.push({
        ...measure(
          label,
          "load",
          () => {
            const c = new Core(pack);
            c.release();
          },
          10,
        ),
        round,
      });
      console.error(`Loaded ${label} round ${round}`);
      const core = new Core(pack);
      if (round === 0) {
        const samples = commands.map((command) => {
          const c = core.createContext(command);
          const result = [
            c.getCommand(),
            c.getStructure(),
            c.getSyntaxTokens(),
            c.getErrorReasons(),
            c.getNodeCount(),
          ];
          for (const cursor of [
            0,
            Math.floor(command.length / 2),
            command.length,
          ])
            result.push([
              c.getAllSuggestions(cursor),
              c.getParamHint(cursor),
              c.applySuggestion(cursor, 0),
            ]);
          c.release();
          return result;
        });
        fingerprints[label] = createHash("sha256")
          .update(JSON.stringify(samples))
          .digest("hex");
      }
      for (const [index, command] of commands.entries()) {
        const cursor = command.length;
        records.push({
          ...measure(label, `request:${index}`, () => {
            const c = core.createContext(command);
            checksum += c.getAllSuggestions(cursor).length;
            checksum += c.getParamHint(cursor).length + c.getStructure().length;
            checksum += c.getSyntaxTokens().length + c.getErrorReasons().length;
            c.release();
          }),
          round,
        });
        const c = core.createContext(command);
        records.push({
          ...measure(label, `suggestions:${index}`, () => {
            checksum += c.getAllSuggestions(cursor).length;
          }),
          round,
        });
        records.push({
          ...measure(label, `nativeSuggestions:${index}`, () => {
            checksum += nativeSuggestionSize(c._contextPtr, cursor);
          }),
          round,
        });
        c.release();
      }
      // Linear memory cannot shrink. Warm-up excludes reusable allocator/buffer capacity.
      const bytesBefore = linearMemoryBytes();
      for (let i = 0; i < 1000; ++i) {
        const c = core.createContext(commands[i % commands.length]);
        c.getAllSuggestions(c.getCommand().length);
        c.getErrorReasons();
        c.release();
      }
      const bytesAfter = linearMemoryBytes();
      records.push({
        variant: label,
        name: "linearMemoryGrowthAfterWarmup",
        round,
        bytes: bytesAfter - bytesBefore,
      });
      core.release();
      console.error(`Completed ${label} round ${round}`);
    }
  }
  if (fingerprints.baseline !== fingerprints.current)
    throw new Error("Wasm result fingerprints differ");
} catch (error) {
  failure = { name: error.name, message: error.message, stack: error.stack };
} finally {
  try {
    await policyPhase("end");
  } catch (error) {
    failure ??= {
      name: error.name,
      message: error.message,
      stack: error.stack,
    };
  }
  try {
    await policyPhase("restore");
  } catch (error) {
    failure ??= {
      name: error.name,
      message: error.message,
      stack: error.stack,
    };
  }
  control?.close();
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
  observer.disconnect();
}
for (const record of records)
  if (record.start !== undefined)
    record.gcEventsOverlapping = gc.filter(
      (entry) =>
        record.start < entry.start + entry.duration && record.end > entry.start,
    ).length;
process.stdout.write(
  JSON.stringify(
    {
      runtime: process.version,
      versions: process.versions,
      execArgv: process.execArgv,
      iterations,
      rounds,
      pack: packPath,
      packSha256: createHash("sha256").update(pack).digest("hex"),
      wasmSha256: variants
        ? Object.fromEntries(
            Object.entries(variants).map(([label, api]) => [
              label,
              api.wasmSha256,
            ]),
          )
        : {},
      sameBinaryCalibration:
        !!variants &&
        variants.baseline.wasmSha256 === variants.current.wasmSha256,
      compiledModuleCount: compiledModules.size,
      independentInstances:
        !!variants &&
        variants.baseline.Core !== variants.current.Core &&
        variants.baseline.nativeSuggestionSize !==
          variants.current.nativeSuggestionSize,
      policyObservations,
      windowsQosEndpointsVerified:
        policyObservations.length === 3 &&
        policyObservations.map((entry) => entry.phase).join(",") ===
          "begin,end,restore" &&
        policyObservations.every((entry) => entry.ok && entry.supported),
      pageFaultCounter:
        process.platform === "win32"
          ? "total process PageFaultCount; not hard-fault classification"
          : "minor plus major process faults",
      gc,
      failure,
      commands,
      checksum,
      fingerprints,
      records,
    },
    null,
    2,
  ) + "\n",
);
if (failure) process.exitCode = 1;
