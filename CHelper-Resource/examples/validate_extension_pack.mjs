#!/usr/bin/env node
/**
 * CHelper 拓展包静态校验 / 打包工具（P1 打包工具的雏形）。
 *
 * 用法:
 *   node validate_extension_pack.mjs <pack_dir> [--pack]
 *
 * 校验内容（与核心实现对齐的静态检查）:
 *   1. 所有 json 可解析、UTF-8 编码；
 *   2. manifest.json 必填字段；
 *   3. command/*.json: name/syntax/node 结构；复刻核心 syntax→node 的 token 匹配
 *      （Serialization.h Codec<NodePerCommand> 的 idMap/trie 逻辑），保证每条 syntax
 *      都能被 node keys 解析；检查 ID/JSON/REPEAT 引用；
 *   4. ID/*.json: id/type/content 结构；
 *   5. extensions/*.json: 片段结构（REPEAT + branches）且被 manifest.extends 引用；
 *   6. selector/*.json: schema 草案结构（仅告警级）。
 *
 * --pack 时把目录内容打成 zip 并改扩展名 .chepack 输出到 pack_dir 同级。
 */
import fs from "node:fs";
import path from "node:path";

const KNOWN_NODE_TYPES = new Set([
  "TEXT", "NORMAL_ID", "NAMESPACE_ID", "TARGET_SELECTOR", "INTEGER", "FLOAT",
  "BOOLEAN", "POSITION", "STRING", "RELATIVE_FLOAT", "BLOCK", "ITEM", "JSON",
  "REPEAT", "INTEGER_WITH_UNIT", "COMMAND_NAME", "RANGE",
  "JSON_STRING", "JSON_LIST", "JSON_OBJECT", "JSON_BOOLEAN", "JSON_INTEGER",
  "JSON_FLOAT", "JSON_NULL",
]);

const REQUIRED_MANIFEST_FIELDS = ["name", "version", "versionCode", "packId"];
const ID_TYPES = new Set(["normal", "namespace", "block", "item"]);

class Report {
  constructor() { this.errors = []; this.warnings = []; }
  error(file, msg) { this.errors.push(`[ERROR] ${file}: ${msg}`); }
  warning(file, msg) { this.warnings.push(`[WARN ] ${file}: ${msg}`); }
  ok(file, msg = "") { console.log(`[ OK  ] ${file}${msg ? ` - ${msg}` : ""}`); }
}

/** 剥离 JSON 注释（// 行注释、/* 块注释，字符串内不处理），与核心 JsonUtil::stripJsonComments 同规则 */
function stripJsonComments(text) {
  let out = "";
  let inString = false;
  let i = 0;
  const n = text.length;
  while (i < n) {
    const c = text[i];
    if (inString) {
      out += c;
      if (c === "\\" && i + 1 < n) { out += text[i + 1]; i += 2; continue; }
      if (c === '"') inString = false;
      i++;
      continue;
    }
    if (c === '"') { inString = true; out += c; i++; continue; }
    if (c === "/" && i + 1 < n) {
      const d = text[i + 1];
      if (d === "/") {
        i += 2;
        while (i < n && text[i] !== "\n" && text[i] !== "\r") i++;
        out += " ";
        continue;
      }
      if (d === "*") {
        i += 2;
        while (i + 1 < n && !(text[i] === "*" && text[i + 1] === "/")) i++;
        i = i + 1 < n ? i + 2 : n;
        out += " ";
        continue;
      }
    }
    out += c;
    i++;
  }
  return out;
}

function loadJson(file, report, tag) {
  try {
    // 与核心一致：资源包 json 允许内联注释（// 与 /* */）
    return JSON.parse(stripJsonComments(fs.readFileSync(file, "utf8")));
  } catch (e) {
    report.error(tag, `json 解析失败: ${e.message}`);
    return null;
  }
}

/** 复刻核心 idMap 生成：'|' 分隔多 token；'<...>'/'[...]' 成对不拆内部。 */
function splitNodeKeyTokens(key) {
  const tokens = [];
  let start = 0;
  const size = key.length;
  while (start < size) {
    let findStart = start;
    if (key[start] === "[" || key[start] === "<") {
      const close = key[start] === "[" ? "]" : ">";
      const idx = key.indexOf(close, start);
      findStart = idx !== -1 ? idx + 1 : start;
    }
    let end = key.indexOf("|", findStart);
    if (end === -1) end = size;
    if (end > start) tokens.push(key.slice(start, end));
    start = end + 1;
  }
  return tokens;
}

/** 对每条 syntax，从第一个空格后按 最长 token 前缀 匹配推进。 */
function checkSyntaxTokens(syntaxList, tokensDesc, report, tag) {
  const failed = [];
  for (const syntax of syntaxList) {
    let pos = syntax.indexOf(" ");
    if (pos === -1) continue; // 无参数分支
    pos += 1;
    while (pos < syntax.length) {
      while (pos < syntax.length && syntax[pos] === " ") pos += 1;
      if (pos >= syntax.length) break;
      let matched = null;
      for (const token of tokensDesc) {
        if (syntax.startsWith(token, pos)) { matched = token; break; }
      }
      if (matched === null) { failed.push(syntax); break; }
      pos += matched.length;
    }
  }
  return failed;
}

function checkCommand(file, data, report, packIdSet) {
  const tag = path.relative(process.cwd(), file);
  if (!Array.isArray(data.name) || data.name.length === 0) report.error(tag, "name 必须是字符串列表且非空");
  if (!Array.isArray(data.syntax) || data.syntax.length === 0) report.error(tag, "syntax 必须是字符串列表");
  const node = data.node;
  if (typeof node !== "object" || node === null || Array.isArray(node)) {
    report.error(tag, "node 必须是对象");
    return;
  }
  // 1) idMap：node key -> token
  const seen = new Set();
  const idMap = [];
  for (const key of Object.keys(node)) {
    for (const token of splitNodeKeyTokens(key)) {
      if (!seen.has(token)) { seen.add(token); idMap.push([token, key]); }
    }
  }
  idMap.sort((a, b) => b[0].length - a[0].length);
  const tokensDesc = idMap.map((kv) => kv[0]);
  // 2) syntax token 匹配
  for (const syntax of data.syntax) {
    if (!syntax.startsWith("/")) report.warning(tag, `syntax 应以 / 开头: ${syntax}`);
  }
  for (const syntax of checkSyntaxTokens(data.syntax, tokensDesc, report, tag)) {
    report.error(tag, `syntax 含无法被 node keys 解析的 token: ${syntax}`);
  }
  // 3) 节点类型与引用
  for (const [key, value] of Object.entries(node)) {
    if (typeof value !== "object" || value === null) { report.error(tag, `node[${key}] 必须是对象`); continue; }
    const ntype = value.type;
    if (!KNOWN_NODE_TYPES.has(ntype)) report.warning(tag, `node[${key}] 未知 type: ${ntype}`);
    if (ntype === "NORMAL_ID" || ntype === "NAMESPACE_ID") {
      if (!("contents" in value) && "key" in value) {
        if (!packIdSet.has(value.key)) report.warning(tag, `node[${key}] key '${value.key}' 不在本包 ID 表，将按主包内置表解析`);
      } else if (!("contents" in value)) {
        report.error(tag, `node[${key}] ${ntype} 需要 key 或 contents`);
      }
    } else if (ntype === "JSON") {
      if (!("key" in value)) report.error(tag, `node[${key}] JSON 需要 key（json/*.json 的 id）`);
    } else if (ntype === "REPEAT") {
      if (!("key" in value)) report.error(tag, `node[${key}] REPEAT 需要 key`);
    }
  }
}

function checkIdFile(file, data, report) {
  const tag = path.relative(process.cwd(), file);
  if (!ID_TYPES.has(data.type)) report.error(tag, `id type 必须为 ${[...ID_TYPES].join("/")}`);
  // normal/namespace 是条目列表；block 是复合对象（与内置 block.json 同构）；
  // item 是条目列表（与内置 item.json 同构：content 为 {name, description} 数组）
  if (data.type === "normal" || data.type === "namespace") {
    if (!Array.isArray(data.content)) report.error(tag, "content 必须是列表");
  } else if (data.type === "block") {
    if (typeof data.content !== "object" || data.content === null || Array.isArray(data.content)) {
      report.error(tag, "content 必须是对象（与内置 block 大表同构）");
    }
  } else if (data.type === "item") {
    if (!Array.isArray(data.content)) report.error(tag, "content 必须是列表（与内置 item 大表同构）");
  }
}

function checkExtensionFragment(file, data, report, extendsTargets) {
  const tag = path.relative(process.cwd(), file);
  const node = data.node ?? {};
  let repeatKey = null;
  let hasBranches = false;
  for (const value of Object.values(node)) {
    if (value && value.type === "REPEAT") {
      repeatKey = value.key;
      hasBranches = "branches" in value;
    }
  }
  if (!extendsTargets.has(repeatKey)) {
    report.error(tag, `片段目标 repeat '${repeatKey}' 未被 manifest.extends.repeat 声明`);
  }
  if (!hasBranches) report.error(tag, "片段节点必须包含 branches（新增分支列表）");
}

function checkSelectorFile(file, data, report) {
  const tag = path.relative(process.cwd(), file);
  if (!Array.isArray(data.variables)) report.warning(tag, "（草案）缺少 variables 列表");
  if (!Array.isArray(data.arguments)) report.warning(tag, "（草案）缺少 arguments 列表");
}

async function main() {
  const argv = process.argv.slice(2);
  if (argv.length < 1) {
    console.error("用法: node validate_extension_pack.mjs <pack_dir> [--pack]");
    process.exit(1);
  }
  const packDir = path.resolve(argv[0]);
  const doPack = argv.includes("--pack");
  if (!fs.existsSync(packDir)) { console.error(`pack dir not found: ${packDir}`); process.exit(1); }

  const report = new Report();
  const manifestPath = path.join(packDir, "manifest.json");
  let manifest = {};
  if (!fs.existsSync(manifestPath)) {
    report.error("manifest.json", "缺少 manifest.json");
  } else {
    manifest = loadJson(manifestPath, report, "manifest.json") ?? {};
  }
  for (const field of REQUIRED_MANIFEST_FIELDS) {
    if (!(field in manifest)) report.error("manifest.json", `缺少必填字段: ${field}`);
  }
  if ("isBasicPack" in manifest && manifest.isBasicPack !== false) {
    report.error("manifest.json", "第三方包 isBasicPack 必须为 false");
  }

  // ID 表
  const packIdSet = new Set();
  const idDir = path.join(packDir, "ID");
  if (fs.existsSync(idDir)) {
    for (const name of fs.readdirSync(idDir).sort()) {
      if (!name.endsWith(".json")) continue;
      const file = path.join(idDir, name);
      const data = loadJson(file, report, path.relative(process.cwd(), file));
      if (data === null) continue;
      checkIdFile(file, data, report);
      if ("id" in data) packIdSet.add(data.id);
    }
  } else {
    report.warning("ID/", "缺少 ID 目录（示例含自定义候选表时应有）");
  }

  // command
  const cmdDir = path.join(packDir, "command");
  if (fs.existsSync(cmdDir)) {
    for (const name of fs.readdirSync(cmdDir).sort()) {
      if (!name.endsWith(".json")) continue;
      const file = path.join(cmdDir, name);
      const data = loadJson(file, report, path.relative(process.cwd(), file));
      if (data !== null) checkCommand(file, data, report, packIdSet);
    }
  } else {
    report.warning("command/", "缺少 command 目录");
  }

  // json 结构目录
  const jsonDir = path.join(packDir, "json");
  if (fs.existsSync(jsonDir)) {
    for (const name of fs.readdirSync(jsonDir).sort()) {
      if (!name.endsWith(".json")) continue;
      const data = loadJson(path.join(jsonDir, name), report, `json/${name}`);
      if (data !== null && !("id" in data)) report.error(`json/${name}`, "缺少 id 字段");
    }
  } else {
    report.warning("json/", "缺少 json 目录（可缺省）");
  }

  // extensions（片段）
  const extendsTargets = new Set(Object.keys((manifest.extends ?? {}).repeat ?? {}));
  const extDir = path.join(packDir, "extensions");
  let fragmentCount = 0;
  if (fs.existsSync(extDir)) {
    for (const name of fs.readdirSync(extDir).sort()) {
      if (!name.endsWith(".json")) continue;
      fragmentCount += 1;
      const file = path.join(extDir, name);
      const data = loadJson(file, report, path.relative(process.cwd(), file));
      if (data !== null) checkExtensionFragment(file, data, report, extendsTargets);
    }
  }
  if (extendsTargets.size > 0 && fragmentCount === 0) {
    report.warning("manifest.json", `extends.repeat 声明了 ${[...extendsTargets].join(",")} 但 extensions/ 无片段文件`);
  }

  // selector（草案级检查）
  const selDir = path.join(packDir, "selector");
  if (fs.existsSync(selDir)) {
    for (const name of fs.readdirSync(selDir).sort()) {
      if (!name.endsWith(".json")) continue;
      const file = path.join(selDir, name);
      const data = loadJson(file, report, path.relative(process.cwd(), file));
      if (data !== null) checkSelectorFile(file, data, report);
    }
  }

  console.log("-".repeat(60));
  if (report.errors.length > 0) {
    for (const line of report.errors) console.log(line);
    console.log(`结果: ${report.errors.length} 个错误（整包将无法通过合成器加载）`);
    process.exit(1);
  }
  for (const line of report.warnings) console.log(line);
  console.log(`结果: 校验通过（${report.warnings.length} 个警告，见上）`);

  if (doPack) {
    const outName = path.basename(packDir) + ".chepack";
    const outPath = path.join(path.dirname(packDir), outName);
    const tmpZip = outPath + ".zip";
    // 用 .NET ZipArchive 打包：条目必须用 '/' 分隔且不写目录条目
    // （PowerShell Compress-Archive 产物用 '\' 且带目录条目，安卓 ZipInputStream 无法识别，
    //  会导致引擎装载失败——与 build_main_pack.mjs 同一修复约定）
    const script = [
      "$ErrorActionPreference='Stop'",
      "Add-Type -AssemblyName System.IO.Compression",
      "Add-Type -AssemblyName System.IO.Compression.FileSystem",
      `$src = '${packDir.replaceAll("'", "''")}'`,
      `$tmp = '${tmpZip.replaceAll("'", "''")}'`,
      "if (Test-Path $tmp) { Remove-Item $tmp -Force }",
      "$fs = [System.IO.File]::Open($tmp, [System.IO.FileMode]::Create)",
      "$archive = New-Object System.IO.Compression.ZipArchive($fs, [System.IO.Compression.ZipArchiveMode]::Create)",
      // 顶层 README.md 是给人看的说明，不进入包产物
      "Get-ChildItem $src -Recurse -File | Where-Object { $_.FullName -ne (Join-Path $src 'README.md') } | ForEach-Object {",
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
    const { spawnSync } = await import("node:child_process");
    const res = spawnSync("powershell", ["-NoProfile", "-Command", script], { encoding: "utf8", stdio: "ignore" });
    if (res.status !== 0) {
      console.error("打包失败：请检查 PowerShell 可用性");
      process.exit(1);
    }
    fs.renameSync(tmpZip, outPath);
    console.log(`已打包: ${outPath}`);
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
