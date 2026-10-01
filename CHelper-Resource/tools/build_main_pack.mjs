#!/usr/bin/env node
/**
 * 主包（内置补全包）制作工具 —— 分层（Layered）版。
 *
 * 六段（beta/release/netease × vanilla/experiment）中相同内容只存一份：
 *
 *   CHelper-Resource/main-pack/
 *   ├─ manifest.json                     ← 聚合清单（isBasicPack:true + layout:"layered" + segments）
 *   ├─ shared/                           ← 全局公共：六段内容完全相同的文件（command/id/json/repeat/text/… 任意数据子目录）
 *   └─ versions/<versionType>/
 *      ├─ shared/                        ← 该版本下 vanilla/experiment 共有（与其它版本不同）
 *      ├─ vanilla/                       ← 段差异（该段独有/与同版本另一分支不同，含段 manifest）
 *      └─ experiment/
 *
 * 装载规则（版本控制器 / Composer 段装载器）——启用段 = <vt>/<branch>：
 *   视图 = shared/ ∪ versions/<vt>/shared/ ∪ versions/<vt>/<branch>/
 *   （后层覆盖前层同名文件；各层内 command/id/json/repeat/text/… 相对路径不变）
 *
 * 构建时全量自检：按该规则合并出的每个文件 hash 必须与
 * resources/<vt>/<branch>/ 对应文件一致（保证与原全量结构等价）。
 *
 * 用法:
 *   node tools/build_main_pack.mjs [<resourcesRoot>] [--zip]
 */
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import crypto from "node:crypto";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const DEFAULT_RESOURCES = path.join(repoRoot, "resources");
const MAIN_PACK_DIR = path.join(repoRoot, "main-pack");

const VTS = ["beta", "release", "netease"];
const BRANCHES = ["vanilla", "experiment"];
const SEGMENTS = VTS.flatMap((vt) => BRANCHES.map((br) => `${vt}/${br}`));

function fail(msg) {
  console.error(msg);
  process.exit(1);
}

/** 收集段目录整树全部 .json（含根 manifest.json 与任意数据子目录如 text/），rel 为包内相对路径；同时校验 json 可解析 */
function collect(dir) {
  const manifestPath = path.join(dir, "manifest.json");
  if (!fs.existsSync(manifestPath)) fail(`缺少 manifest.json: ${manifestPath}`);
  const out = new Map();
  const walk = (cur, rel) => {
    for (const entry of fs.readdirSync(cur, { withFileTypes: true })) {
      const full = path.join(cur, entry.name);
      if (entry.isDirectory()) walk(full, rel ? `${rel}/${entry.name}` : entry.name);
      else if (entry.name.endsWith(".json")) {
        const relPath = rel ? `${rel}/${entry.name}` : entry.name;
        const bytes = fs.readFileSync(full);
        JSON.parse(bytes.toString("utf8")); // 校验可解析
        out.set(relPath, { bytes, hash: crypto.createHash("sha1").update(bytes).digest("hex") });
      }
    }
  };
  walk(dir, "");
  if (!out.has("manifest.json")) fail(`缺少 manifest.json: ${manifestPath}`);
  return out;
}

/** 归层：返回 { shared, vtShared, diffs, counts }
 *  规则（简单可预期，每 rel 独立判定）：
 *   1. 六段都有且 hash 全同            → shared/（全局一份）
 *   2. 某 versionType 两分支 hash 相同 → versions/<vt>/shared/（该 vt 两段共用）
 *   3. 其余出现该 rel 的段             → versions/<vt>/<branch>/（段差异，每段一份）
 */
function classify(trees) {
  const allRels = new Set();
  for (const s of SEGMENTS) for (const rel of trees[s].keys()) allRels.add(rel);

  const shared = new Map();
  const vtShared = { beta: new Map(), release: new Map(), netease: new Map() };
  const diffs = Object.fromEntries(SEGMENTS.map((s) => [s, new Map()]));
  const counts = { sharedFiles: 0, sharedBytes: 0, vtSharedFiles: 0, vtSharedBytes: 0, diffFiles: 0, diffBytes: 0 };

  for (const rel of allRels) {
    const inAll = SEGMENTS.every((s) => trees[s].has(rel));
    const first = trees[SEGMENTS[0]].get(rel);
    const hashes = SEGMENTS.map((s) => trees[s].get(rel)?.hash);
    if (inAll && hashes.every((h) => h === hashes[0])) {
      shared.set(rel, first);
      counts.sharedFiles++;
      counts.sharedBytes += first.bytes.length;
      continue;
    }
    // 逐 versionType：两分支相同 → 该 vt 的 shared 层覆盖两段
    const covered = new Set();
    for (const vt of VTS) {
      const a = trees[`${vt}/vanilla`].get(rel);
      const b = trees[`${vt}/experiment`].get(rel);
      if (a && b && a.hash === b.hash) {
        vtShared[vt].set(rel, a);
        counts.vtSharedFiles++;
        counts.vtSharedBytes += a.bytes.length;
        covered.add(`${vt}/vanilla`);
        covered.add(`${vt}/experiment`);
      }
    }
    // 未被 vt shared 覆盖的段，各自保留差异副本
    for (const s of SEGMENTS) {
      if (covered.has(s)) continue;
      const item = trees[s].get(rel);
      if (item) {
        diffs[s].set(rel, item);
        counts.diffFiles++;
        counts.diffBytes += item.bytes.length;
      }
    }
  }
  return { shared, vtShared, diffs, counts };
}

/** 装载自检：按 shared→vt shared→branch 合并后，每 rel hash 与源一致 */
function verify(trees, { shared, vtShared, diffs }) {
  let checked = 0;
  for (const s of SEGMENTS) {
    const [vt, br] = s.split("/");
    const view = new Map(shared);
    for (const [rel, item] of vtShared[vt]) view.set(rel, item);
    for (const [rel, item] of diffs[s]) view.set(rel, item);
    for (const [rel, item] of trees[s]) {
      const got = view.get(rel);
      if (!got || got.hash !== item.hash) {
        fail(`自检失败: ${s}/${rel} 合并结果与源不一致`);
      }
      checked++;
    }
  }
  return checked;
}

function main() {
  const argv = process.argv.slice(2);
  const positional = argv.filter((a) => !a.startsWith("--"));
  const doZip = argv.includes("--zip");
  const resourcesRoot = positional[0] ? path.resolve(positional[0]) : DEFAULT_RESOURCES;
  if (!fs.existsSync(resourcesRoot)) fail(`resources 目录不存在: ${resourcesRoot}`);

  // 收集六段
  const trees = {};
  const segmentsMeta = {};
  for (const s of SEGMENTS) {
    const [vt, br] = s.split("/");
    const dir = path.join(resourcesRoot, vt, br);
    if (!fs.existsSync(dir)) fail(`段目录不存在: ${dir}`);
    trees[s] = collect(dir);
    const m = JSON.parse(trees[s].get("manifest.json").bytes.toString("utf8"));
    segmentsMeta[s] = { version: m.version, packId: m.packId, name: m.name };
  }

  // 归层 + 自检
  const { shared, vtShared, diffs, counts } = classify(trees);
  const checked = verify(trees, { shared, vtShared, diffs });

  // 写目录源
  fs.rmSync(MAIN_PACK_DIR, { recursive: true, force: true });
  fs.mkdirSync(MAIN_PACK_DIR, { recursive: true });
  const put = (layer, rel, item) => {
    const dst = path.join(MAIN_PACK_DIR, layer, rel);
    fs.mkdirSync(path.dirname(dst), { recursive: true });
    fs.writeFileSync(dst, item.bytes);
  };
  for (const [rel, item] of shared) put("shared", rel, item);
  for (const vt of VTS) for (const [rel, item] of vtShared[vt]) put(`versions/${vt}/shared`, rel, item);
  for (const s of SEGMENTS) for (const [rel, item] of diffs[s]) put(`versions/${s.replace("/", "/")}`, rel, item);

  // 聚合 manifest
  const mainManifest = {
    name: "CHelper 内置主包（全版本原版数据，分层）",
    description:
      "内置补全包：六段（beta/release/netease × vanilla/experiment）相同内容只存一份，" +
      "按 shared → versions/<vt>/shared → versions/<vt>/<branch> 分层装载（后层覆盖同名）。",
    version: "1.1.0",
    versionCode: 2,
    versionType: "main",
    branch: "main",
    author: "Yancey",
    updateDate: new Date().toISOString().slice(0, 10),
    packId: "chelper-main-pack",
    isBasicPack: true,
    isDefault: true,
    layout: "layered",
    segments: Object.fromEntries(
      VTS.map((vt) => [
        vt,
        Object.fromEntries(
          BRANCHES.map((br) => {
            const meta = segmentsMeta[`${vt}/${br}`];
            return [br, { version: meta.version, packId: meta.packId, name: meta.name }];
          }),
        ),
      ]),
    ),
  };
  fs.writeFileSync(path.join(MAIN_PACK_DIR, "manifest.json"), JSON.stringify(mainManifest, null, 2), "utf8");

  // 统计目录源文件数
  const countDirFiles = (d) => {
    let n = 0;
    const walk = (cur) => { for (const e of fs.readdirSync(cur, { withFileTypes: true })) { const f = path.join(cur, e.name); if (e.isDirectory()) walk(f); else n++; } };
    walk(d);
    return n;
  };

  console.log("=".repeat(60));
  console.log(`[OK] 分层主包目录源: ${MAIN_PACK_DIR}`);
  console.log(`     自检通过: ${checked} 个文件合并后与源完全一致`);
  console.log(`     全局 shared: ${counts.sharedFiles} 个文件`);
  for (const vt of VTS) console.log(`     versions/${vt}/shared: ${vtShared[vt].size} 个文件`);
  for (const s of SEGMENTS) console.log(`     versions/${s}: ${diffs[s].size} 个差异文件`);
  const orig = SEGMENTS.reduce((s, x) => s + trees[x].size, 0);
  console.log(`     源 ${orig} 个文件 → 分层后 ${countDirFiles(MAIN_PACK_DIR) - 1} 个（不含聚合 manifest），省 ${(((orig - (countDirFiles(MAIN_PACK_DIR) - 1)) / orig) * 100).toFixed(0)}%`);

  if (doZip) {
    const outDir = path.join(repoRoot, "generated", "main-packs");
    fs.mkdirSync(outDir, { recursive: true });
    const outPath = path.join(outDir, "main-pack.chepack");
    const tmpZip = outPath + ".tmp.zip";
    // 用 .NET ZipArchive 打包：条目必须用 '/' 分隔且不写目录条目
    // （PowerShell Compress-Archive 产物用 '\' 且带目录条目，安卓 ZipInputStream 无法识别，
    //  空字节目录条目会破坏引擎装载——见 CHelper-Core MainPack::open 注释）
    const script = [
      "$ErrorActionPreference='Stop'",
      "Add-Type -AssemblyName System.IO.Compression",
      "Add-Type -AssemblyName System.IO.Compression.FileSystem",
      `$src = '${MAIN_PACK_DIR.replaceAll("'", "''")}'`,
      `$tmp = '${tmpZip.replaceAll("'", "''")}'`,
      "if (Test-Path $tmp) { Remove-Item $tmp -Force }",
      "$fs = [System.IO.File]::Open($tmp, [System.IO.FileMode]::Create)",
      "$archive = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)",
      "Get-ChildItem $src -Recurse -File | ForEach-Object {",
      "  $rel = $_.FullName.Substring($src.Length + 1).Replace('\\', '/')",
      "  $entry = $archive.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)",
      "  $es = $entry.Open()",
      "  $bytes = [System.IO.File]::ReadAllBytes($_.FullName)",
      "  $es.Write($bytes, 0, $bytes.Length)",
      "  $es.Dispose()",
      "}",
      "$archive.Dispose()",
      "$fs.Dispose()",
    ].join("; ");
    const res = spawnSync("powershell", ["-NoProfile", "-Command", script], { stdio: "ignore" });
    if (res.status !== 0) fail(`zip 失败: ${outPath}`);
    fs.renameSync(tmpZip, outPath);
    console.log(`[OK] 单文件主包: ${outPath}（${(fs.statSync(outPath).size / 1024 / 1024).toFixed(2)} MB）`);
  }
}

main();
