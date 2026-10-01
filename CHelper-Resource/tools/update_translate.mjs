#!/usr/bin/env node
/**
 * translate 数据来源更新脚本（R2b）。
 *
 * 把多来源翻译数据合并成主包 text/translate.json（对象 {翻译键: 中文}）：
 *   - base : 现有基线（对象 {key: 中文}，缺译时值可能回退为键名）
 *   - lang : 官方 zh_CN.lang（"key=中文" 行，UTF-8）
 *   - en   : 官方英文键表（对象 {key: en}，作为键全集与回退源）
 *   - wiki : 中文 Minecraft Wiki 增量（对象 {key: 中文} 或 CSV "key,中文"）
 *
 * 合并规则：键并集 = base ∪ en ∪ lang ∪ wiki；
 *           中文优先级 wiki > lang > base（值非空才用）；缺译 → 回退 en 原文，再回退键名。
 * 输出 translate.json（对象）+ meta.json（键总数 / 缺译数 / 各来源贡献数）。
 *
 * 用法:
 *   node tools/update_translate.mjs --base <基线.json> [--lang <zh_CN.lang>] [--en <en_us.json>]
 *                                   [--wiki <wiki.json|csv>] --out <text/translate.json> [--meta <meta.json>]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
// 默认基线：release/vanilla 分支的 text/translate.json（与主包 text/ 同源；
// 旧的 CHelper-Android assets/rawtext/translate.json 已随主路径切换移除）
const DEFAULT_BASE = path.join(__dirname, "..", "resources", "release", "vanilla", "text", "translate.json");

function fail(msg) {
  console.error(msg);
  process.exit(1);
}

function readJson(p) {
  return JSON.parse(fs.readFileSync(p, "utf8"));
}

function parseLang(p) {
  const out = new Map();
  for (const line of fs.readFileSync(p, "utf8").split(/\r?\n/)) {
    const idx = line.indexOf("=");
    if (idx <= 0) continue;
    const key = line.slice(0, idx);
    const val = line.slice(idx + 1);
    if (val) out.set(key, val);
  }
  return out;
}

function parseWiki(p) {
  const out = new Map();
  const text = fs.readFileSync(p, "utf8").trim();
  try {
    const obj = JSON.parse(text);
    for (const [k, v] of Object.entries(obj)) if (v) out.set(k, String(v));
    return out;
  } catch {
    // 当作 CSV：key,中文
    for (const line of text.split(/\r?\n/)) {
      const idx = line.indexOf(",");
      if (idx > 0) {
        const key = line.slice(0, idx).trim();
        const val = line.slice(idx + 1).trim();
        if (key && val) out.set(key, val);
      }
    }
    return out;
  }
}

function main() {
  const argv = process.argv.slice(2);
  const opt = (name, def) => {
    const i = argv.indexOf(name);
    return i !== -1 && i + 1 < argv.length ? argv[i + 1] : def;
  };
  const basePath = opt("--base", DEFAULT_BASE);
  const langPath = opt("--lang", null);
  const enPath = opt("--en", null);
  const wikiPath = opt("--wiki", null);
  const outPath = opt("--out", null);
  const metaPath = opt("--meta", null);

  if (!outPath) fail("缺少 --out 输出路径");
  const base = fs.existsSync(basePath) ? readJson(basePath) : {};
  const en = enPath && fs.existsSync(enPath) ? readJson(enPath) : {};
  const lang = langPath && fs.existsSync(langPath) ? parseLang(langPath) : new Map();
  const wiki = wikiPath && fs.existsSync(wikiPath) ? parseWiki(wikiPath) : new Map();

  const keys = new Set([...Object.keys(base), ...Object.keys(en), ...lang.keys(), ...wiki.keys()]);
  const out = {};
  let missing = 0;
  const contrib = { wiki: 0, lang: 0, base: 0, en: 0, keyFallback: 0 };
  for (const key of keys) {
    let zh = wiki.get(key) || lang.get(key) || base[key];
    if (zh) {
      if (wiki.has(key)) contrib.wiki++;
      else if (lang.has(key)) contrib.lang++;
      else contrib.base++;
    } else if (en[key]) {
      zh = en[key];
      contrib.en++;
      missing++;
    } else {
      zh = key;
      contrib.keyFallback++;
      missing++;
    }
    out[key] = zh;
  }

  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(out, null, 2), "utf8");
  const meta = {
    total: keys.size,
    missing,
    sources: { ...contrib },
    inputs: { base: basePath, lang: langPath, en: enPath, wiki: wikiPath },
  };
  if (metaPath) {
    fs.mkdirSync(path.dirname(metaPath), { recursive: true });
    fs.writeFileSync(metaPath, JSON.stringify(meta, null, 2), "utf8");
  }
  console.log(`[OK] ${outPath}`);
  console.log(`     键总数 ${meta.total}，缺译 ${meta.missing}（回退英文/键名）`);
  console.log(`     来源贡献 wiki=${contrib.wiki} lang=${contrib.lang} base=${contrib.base} en=${contrib.en} keyFallback=${contrib.keyFallback}`);
}

main();
