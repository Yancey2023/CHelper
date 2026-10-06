//! 生成管线编排：拉取数据源 → 抓取翻译资源 → 逐类别翻译匹配 → 写出 chelper 资源包。

use crate::cache::{HttpCache, RepoSource};
use crate::chelper::{write_resource_pack, ChelperInput};
use crate::config::Config;
use crate::sources::bedrock_lang::fetch_bedrock_lang;
use crate::sources::caidlist::{remove_minecraft_namespace, Enums, RepoSnapshot};
use crate::sources::java_lang::{fetch_java_lang, JavaLang};
use crate::sources::wiki::{
    fetch_block_property_descriptions, fetch_gamerule_descriptions, fetch_gamerule_type_value,
    fetch_particle_descriptions, fetch_standardized_translation,
};
use crate::support;
use crate::translate::{st_priority, CategoryLevels, DataDrivenContext, ResultMaps, Translator};
use crate::{jfmt::to_string_pretty4, write_crlf};
use anyhow::{bail, Context, Result};
use indexmap::IndexMap;
use serde_json::Value;
use std::path::Path;
use std::sync::Arc;

#[derive(Debug, serde::Serialize)]
pub struct BranchReport {
    pub branch: String,
    pub files: usize,
    pub entries: usize,
    pub untranslated_ids: usize,
    pub partially_untranslated_categories: usize,
}

#[derive(Debug, serde::Serialize)]
pub struct EditionReport {
    pub edition: String,
    pub package_version: String,
    pub branches: Vec<BranchReport>,
}

#[derive(Debug, serde::Serialize, Default)]
pub struct GenerateReport {
    pub editions: Vec<EditionReport>,
    pub warnings: usize,
    pub untranslated_ids: usize,
}

/// 支持的版本：release / beta 用 master 的对应数据目录；netease 用 netease 源的 version/release。
fn version_dir_for(edition: &str) -> &'static str {
    match edition {
        "beta" => "version/beta",
        "release" | "netease" => "version/release",
        other => unreachable!("未知版本 {other}"),
    }
}

pub async fn generate(cfg: &Config, editions: &[String], refresh: bool) -> Result<GenerateReport> {
    let http = Arc::new(HttpCache::new(&cfg.cache_dir)?);
    let mut report = GenerateReport::default();
    let mut untranslated_warnings = Vec::new();

    // —— 数据源快照 ——
    let master_source = RepoSource::new(
        "master",
        &cfg.master,
        http.clone(),
        &cfg.cache_dir,
        cfg.ref_ttl_minutes,
        refresh,
    );
    let master_snapshot = RepoSnapshot::new(master_source.checkout().await?);

    // —— 共享外部资源（一次抓取，多版本复用）——
    tracing::info!("抓取 wiki 标准译名表…");
    let standardized_translation = fetch_standardized_translation(&http).await?;
    tracing::info!("抓取 wiki 游戏规则类型表…");
    let gamerule_type = fetch_gamerule_type_value(&http).await?;
    tracing::info!("抓取 wiki 方块属性描述…");
    let block_property_descriptions = fetch_block_property_descriptions(&http).await?;
    tracing::info!("抓取 wiki 游戏规则描述（第 2 优先级）…");
    let gamerule_wiki = fetch_gamerule_descriptions(&http).await?;
    tracing::info!("抓取 wiki 基岩版粒子描述（第 2 优先级）…");
    let particle_wiki = fetch_particle_descriptions(&http).await?;
    tracing::info!("抓取 Java 版语言文件…");
    let java_lang = fetch_java_lang(&http, &cfg.java_manifest_url).await?;

    // —— 项目手工数据 ——
    let game_mode = load_jsonc_file(&cfg.data_dir.join("game_mode.json"))
        .context("缺少 data/game_mode.json（游戏模式手工数据）")?;
    let potion_descriptions = load_jsonc_file(&cfg.data_dir.join("potion_descriptions.json"))
        .context("缺少 data/potion_descriptions.json（药水描述数据）")?;

    for edition in editions {
        if !matches!(edition.as_str(), "release" | "beta" | "netease") {
            bail!("未知版本: {edition}（支持 release / beta / netease）");
        }
        let (snapshot, version_dir) = if edition == "netease" {
            let source = RepoSource::new(
                "netease",
                &cfg.netease,
                http.clone(),
                &cfg.cache_dir,
                cfg.ref_ttl_minutes,
                refresh,
            );
            let snapshot = RepoSnapshot::new(source.checkout().await?);
            (snapshot, version_dir_for("netease"))
        } else {
            (master_snapshot.clone(), version_dir_for(edition))
        };
        let package_version = Enums::package_version(&snapshot, version_dir)
            .with_context(|| format!("[{edition}] 读取包版本失败"))?;
        // 特性开关使用 coreVersion（对齐 generate.js：无配置时等于包版本）
        let core_version = package_version.clone();

        // 基岩版语言文件引用
        let bedrock_ref = match edition.as_str() {
            "release" => cfg.bedrock_lang.release_ref.clone(),
            "beta" => cfg.bedrock_lang.beta_ref.clone(),
            _ => {
                if cfg.bedrock_lang.netease_ref.is_empty() {
                    cfg.bedrock_lang.release_ref.clone()
                } else {
                    cfg.bedrock_lang.netease_ref.clone()
                }
            }
        };
        tracing::info!("[{edition}] 抓取基岩版语言文件（引用 {bedrock_ref}）…");
        let bedrock_lang = fetch_bedrock_lang(
            &http,
            &bedrock_ref,
            &package_version,
            cfg.bedrock_lang.try_version_tag,
        )
        .await?;

        // block.json 的属性数据固定取 beta/gametest 数据（与 chelper.js 一致）；
        // netease 数据源自带 beta 数据时优先使用。
        let gametest: Value = if edition == "netease" && snapshot.exists("version/beta/gametest/all.json") {
            snapshot.read_jsonc("version/beta/gametest/all.json")?
        } else {
            master_snapshot.read_jsonc("version/beta/gametest/all.json")?
        };

        let mut branch_reports = Vec::new();
        for branch in ["vanilla", "experiment"] {
            tracing::info!("[{edition}/{branch}] 加载枚举并匹配翻译…");
            let enums = Enums::load(&snapshot, version_dir, branch)
                .with_context(|| format!("[{edition}/{branch}] 加载枚举失败"))?;
            let (result_maps, warnings) = match_branches(
                cfg,
                edition,
                branch,
                &core_version,
                &enums,
                &snapshot,
                &standardized_translation,
                &java_lang,
                &bedrock_lang,
                &gamerule_wiki,
            )?;
            for w in &warnings {
                tracing::debug!("[{edition}/{branch}] {w}");
            }

            let (untranslated_count, partial_categories, branch_warnings) =
                write_untranslated_ids(&cfg.untranslated_dir, edition, branch, &result_maps)?;
            report.untranslated_ids += untranslated_count;
            report.warnings += branch_warnings.len();
            untranslated_warnings.extend(branch_warnings);

            let output_dir = cfg.output_dir.join(edition).join(branch).join("id");
            let input = ChelperInput {
                result_maps: &result_maps,
                gamerule_type: &gamerule_type,
                block_property_descriptions: &block_property_descriptions,
                gametest: &gametest,
                potion_descriptions: &potion_descriptions,
                game_mode: &game_mode,
                particle_emitter: &particle_wiki,
            };
            let files = write_resource_pack(input, &output_dir)
                .with_context(|| format!("[{edition}/{branch}] 写出资源包失败"))?;
            let entries: usize = result_maps.values().map(|m| m.len()).sum();
            tracing::info!(
                "[{edition}/{branch}] 写出 {files} 个文件（{entries} 条目，{} 条警告）",
                warnings.len()
            );
            branch_reports.push(BranchReport {
                branch: branch.to_string(),
                files,
                entries,
                untranslated_ids: untranslated_count,
                partially_untranslated_categories: partial_categories,
            });
            report.warnings += warnings.len();
        }
        report.editions.push(EditionReport {
            edition: edition.to_string(),
            package_version,
            branches: branch_reports,
        });
    }
    for warning in untranslated_warnings {
        tracing::warn!("{warning}");
    }
    Ok(report)
}

/// Write one JSON ID array per category and remove stale/empty category files.
fn write_untranslated_ids(
    root: &Path,
    edition: &str,
    branch: &str,
    result_maps: &ResultMaps,
) -> Result<(usize, usize, Vec<String>)> {
    let dir = root.join(edition).join(branch);
    std::fs::create_dir_all(&dir)
        .with_context(|| format!("创建未翻译 ID 目录失败: {}", dir.display()))?;

    let mut expected_files = std::collections::HashSet::new();
    let mut missing_total = 0usize;
    let mut partial_categories = 0usize;
    let mut warnings = Vec::new();
    for (category, values) in result_maps {
        let missing: Vec<String> = values
            .iter()
            .filter(|(_, translation)| translation.trim().is_empty())
            .map(|(id, _)| id.clone())
            .collect();
        if missing.is_empty() {
            continue;
        }
        let translated = values.len() - missing.len();
        let file_name = format!("{category}.json");
        expected_files.insert(file_name.clone());
        missing_total += missing.len();
        let path = dir.join(&file_name);
        let content = serde_json::Value::Array(
            missing.iter().cloned().map(serde_json::Value::String).collect(),
        );
        write_crlf(&path, &to_string_pretty4(&content))
            .with_context(|| format!("写入未翻译 ID 清单失败: {}", path.display()))?;

        if translated > 0 {
            partial_categories += 1;
            warnings.push(format!(
                "[{edition}/{branch}] 类别 {category} 有部分 ID 未翻译：{translated}/{} 个 ID 已翻译，{} 个缺失（清单：{}）",
                values.len(),
                missing.len(),
                path.display()
            ));
        }
    }

    for entry in std::fs::read_dir(&dir)
        .with_context(|| format!("读取未翻译 ID 目录失败: {}", dir.display()))?
    {
        let entry = entry?;
        let path = entry.path();
        if path.extension().and_then(|ext| ext.to_str()) == Some("json")
            && !expected_files.contains(&entry.file_name().to_string_lossy().to_string())
        {
            std::fs::remove_file(&path)
                .with_context(|| format!("删除过期未翻译 ID 清单失败: {}", path.display()))?;
        }
    }

    Ok((missing_total, partial_categories, warnings))
}

#[allow(clippy::too_many_arguments)]
fn match_branches(
    cfg: &Config,
    edition: &str,
    branch: &str,
    core_version: &str,
    enums: &Enums,
    snapshot: &RepoSnapshot,
    st: &Value,
    java_lang: &JavaLang,
    bedrock_lang: &IndexMap<String, String>,
    gamerule_wiki: &IndexMap<String, String>,
) -> Result<(ResultMaps, Vec<String>)> {
    let je_map = java_lang.zh_cn.as_object().cloned().unwrap_or_default();
    let be_map: serde_json::Map<String, Value> = bedrock_lang
        .iter()
        .map(|(k, v)| (k.clone(), Value::String(v.clone())))
        .collect();
    let data_driven = enums
        .object("dataDrivenRecipeData")
        .map(|recipe_data| DataDrivenContext { recipe_data });
    let mut translator = Translator::new(&je_map, &be_map, data_driven);

    fn run<'a>(
        translator: &mut Translator<'a>,
        st: &'a Value,
        name: &str,
        original_array: Vec<String>,
        levels: &CategoryLevels,
        priority: &'static [&'static str],
    ) {
        translator.match_translations(name, &original_array, levels, st, priority);
    }

    // block
    let levels = build_levels(cfg, snapshot, "block", None)?;
    run(&mut translator, st, "block", enums.array("blocks"), &levels, st_priority::BLOCK);
    // item：不在 blocks 中的物品，或在任一翻译来源中有条目的
    let levels = build_levels(cfg, snapshot, "item", None)?;
    let blocks = enums.array("blocks");
    let item_array: Vec<String> = enums
        .array("items")
        .into_iter()
        .filter(|item| !blocks.contains(item) || levels.contains(item))
        .collect();
    run(&mut translator, st, "item", item_array, &levels, st_priority::ITEM);
    postprocess_item_merge(&mut translator, enums);
    // entity
    let levels = build_levels(cfg, snapshot, "entity", None)?;
    let entity_array = remove_minecraft_namespace(&enums.array("entities"));
    run(&mut translator, st, "entity", entity_array, &levels, st_priority::ENTITY);
    postprocess_namespace_merge(&mut translator, "entity", "entities", enums, MergeMode::Prefix);
    // effect / enchant / fog
    let levels = build_levels(cfg, snapshot, "effect", None)?;
    run(&mut translator, st, "effect", enums.array("effects"), &levels, st_priority::EFFECT);
    let levels = build_levels(cfg, snapshot, "enchant", None)?;
    run(&mut translator, st, "enchant", enums.array("enchantments"), &levels, st_priority::ENCHANT);
    let levels = build_levels(cfg, snapshot, "fog", None)?;
    run(&mut translator, st, "fog", enums.array("fogs"), &levels, st_priority::BIOME);
    // location（原始数组去 minecraft: 前缀，结果表恢复原始键序）
    let levels = build_levels(cfg, snapshot, "location", None)?;
    let locations: Vec<String> = enums
        .array("locations")
        .iter()
        .map(|e| e.strip_prefix("minecraft:").unwrap_or(e).to_string())
        .collect();
    run(&mut translator, st, "location", locations, &levels, st_priority::ENV);
    postprocess_namespace_merge(&mut translator, "location", "locations", enums, MergeMode::Strip);
    // biome（版本门控）
    if support::new_locate_command(core_version) {
        let levels = build_levels(cfg, snapshot, "biome", None)?;
        let biomes: Vec<String> = enums
            .array("biomes")
            .iter()
            .map(|e| e.strip_prefix("minecraft:").unwrap_or(e).to_string())
            .collect();
        run(&mut translator, st, "biome", biomes, &levels, st_priority::BIOME);
        postprocess_namespace_merge(&mut translator, "biome", "biomes", enums, MergeMode::Strip);
    }
    // 实体相关（package 数据派生）
    let levels = build_levels(cfg, snapshot, "entity_event", None)?;
    run(
        &mut translator,
        st,
        "entityEvent",
        enums.object_keys("entityEventsMap"),
        &levels,
        st_priority::ENTITY,
    );
    let levels = build_levels(cfg, snapshot, "entity_family", None)?;
    run(
        &mut translator,
        st,
        "entityFamily",
        enums.object_keys("entityFamilyMap"),
        &levels,
        st_priority::ENTITY,
    );
    let levels = build_levels(cfg, snapshot, "animation", None)?;
    run(
        &mut translator,
        st,
        "animation",
        enums.object_keys("animationMap"),
        &levels,
        st_priority::ENTITY,
    );
    let levels = build_levels(cfg, snapshot, "animation_controller", None)?;
    run(
        &mut translator,
        st,
        "animationController",
        enums.object_keys("animationControllerMap"),
        &levels,
        st_priority::ENTITY,
    );
    // sound（音效翻译依赖 translation/sound.json 中的 JE 引用）
    let levels = build_levels(cfg, snapshot, "sound", None)?;
    run(&mut translator, st, "sound", enums.array("sounds"), &levels, st_priority::FULL);
    // gamerule（第 2 级 = wiki 游戏规则描述）
    let levels = build_levels(cfg, snapshot, "gamerule", Some(gamerule_wiki))?;
    run(&mut translator, st, "gamerule", enums.array("gamerules"), &levels, st_priority::FULL);
    // entitySlot
    let levels = build_levels(cfg, snapshot, "entity_slot", None)?;
    run(&mut translator, st, "entitySlot", enums.array("entitySlots"), &levels, st_priority::FULL);
    // lootTable + lootTableWrapped
    if support::loot_table(core_version) {
        let levels = build_levels(cfg, snapshot, "loot_table", None)?;
        run(&mut translator, st, "lootTable", enums.array("lootTables"), &levels, st_priority::FULL);
        let wrapped: IndexMap<String, String> = translator
            .results
            .get("lootTable")
            .map(|m| {
                let mut wrapped = IndexMap::new();
                for (key, value) in m {
                    let quoted = serde_json::to_string(key).unwrap_or_default();
                    wrapped.insert(quoted, value.clone());
                    if !key.contains('/') {
                        wrapped.insert(key.clone(), value.clone());
                    }
                }
                wrapped
            })
            .unwrap_or_default();
        translator.results.insert("lootTableWrapped".into(), wrapped);
    } else {
        translator.results.insert("lootTable".into(), IndexMap::new());
        translator.results.insert("lootTableWrapped".into(), IndexMap::new());
    }
    // damageCause
    if support::damage_command(core_version) {
        let levels = build_levels(cfg, snapshot, "damage_cause", None)?;
        run(&mut translator, st, "damageCause", enums.array("damageCauses"), &levels, st_priority::FULL);
    }
    // inputPermission
    if support::inputpermission_command(core_version) {
        let levels = build_levels(cfg, snapshot, "input_permission", None)?;
        run(
            &mut translator,
            st,
            "inputPermission",
            enums.array("inputPermissions"),
            &levels,
            st_priority::FULL,
        );
    }
    // cameraPreset / cameraEasing
    if support::camera_command(core_version, branch) {
        let levels = build_levels(cfg, snapshot, "camera_preset", None)?;
        let presets: Vec<String> = enums
            .array("cameraPresets")
            .into_iter()
            .filter(|e| !e.starts_with("example:"))
            .collect();
        run(&mut translator, st, "cameraPreset", presets, &levels, st_priority::FULL);
        let levels = build_levels(cfg, snapshot, "camera_easing", None)?;
        run(&mut translator, st, "cameraEasing", enums.array("cameraEasings"), &levels, st_priority::FULL);
    }
    // recipe（dataDriven 引用解析）
    if support::recipe_new_command(core_version, branch) {
        let levels = build_levels(cfg, snapshot, "recipe", None)?;
        run(&mut translator, st, "recipe", enums.array("recipes"), &levels, st_priority::FULL);
    }
    // hudElement
    if support::hud_command(core_version, branch) {
        let levels = build_levels(cfg, snapshot, "hud_element", None)?;
        run(&mut translator, st, "hudElement", enums.array("hudElements"), &levels, st_priority::FULL);
    }
    // feature / featureRule
    if support::place_command_feature_sub_command(core_version, branch) {
        let levels = build_levels(cfg, snapshot, "feature", None)?;
        run(&mut translator, st, "feature", enums.array("features"), &levels, st_priority::FULL);
        let levels = build_levels(cfg, snapshot, "feature_rule", None)?;
        run(&mut translator, st, "featureRule", enums.array("featureRules"), &levels, st_priority::FULL);
    }
    // controlScheme
    if support::control_scheme_command(core_version, branch) {
        let levels = build_levels(cfg, snapshot, "control_schemes", None)?;
        run(&mut translator, st, "controlScheme", enums.array("controlSchemes"), &levels, st_priority::FULL);
    }
    // music：sound 的 music./record. 子集
    let music: IndexMap<String, String> = translator
        .results
        .get("sound")
        .map(|m| {
            m.iter()
                .filter(|(k, _)| k.starts_with("music.") || k.starts_with("record."))
                .map(|(k, v)| (k.clone(), v.clone()))
                .collect()
        })
        .unwrap_or_default();
    translator.results.insert("music".into(), music);

    // netease 多出内容（仅叠加到已存在的类别）
    if edition == "netease" {
        apply_netease_extras(cfg, branch, &mut translator)?;
    }

    let warnings = translator.take_warnings();
    Ok((translator.results, warnings))
}

/// postProcessor 的回退查找方向（复刻 generate.js 中两类 postProcessor）。
#[derive(Clone, Copy, PartialEq)]
enum MergeMode {
    /// location/biome：`location[key.replace(/^minecraft:/, '')]`
    Strip,
    /// entity：`entity['minecraft:' + key]`
    Prefix,
}

/// 复刻 location/biome/entity 的 postProcessor：以原始（可能带命名空间）键序重建结果表。
fn postprocess_namespace_merge(
    translator: &mut Translator<'_>,
    category: &str,
    enum_name: &str,
    enums: &Enums,
    mode: MergeMode,
) {
    let Some(result) = translator.results.get(category) else {
        return;
    };
    let mut merged = IndexMap::new();
    for key in enums.array(enum_name) {
        if let Some(v) = result.get(&key) {
            merged.insert(key.clone(), v.clone());
        } else {
            let fallback_key = match mode {
                MergeMode::Strip => key.strip_prefix("minecraft:").unwrap_or(&key).to_string(),
                MergeMode::Prefix => format!("minecraft:{key}"),
            };
            merged.insert(key.clone(), result.get(&fallback_key).cloned().unwrap_or_default());
        }
    }
    translator.results.insert(category.to_string(), merged);
}

/// 复刻 item 的 postProcessor：按 enums.items 顺序合并 item 与 block 的翻译。
fn postprocess_item_merge(translator: &mut Translator<'_>, enums: &Enums) {
    let mut merged = IndexMap::new();
    {
        let Some(item) = translator.results.get("item") else {
            return;
        };
        let Some(block) = translator.results.get("block") else {
            return;
        };
        for key in enums.array("items") {
            match item.get(&key) {
                Some(v) => {
                    merged.insert(key, v.clone());
                }
                None => {
                    merged.insert(key.clone(), block.get(&key).cloned().unwrap_or_default());
                }
            }
        }
    }
    translator.results.insert("item".into(), merged);
}

/// netease 多出内容：`netease_extra/<branch>/<输出类别名>.json`（JSONC 对象：ID → 描述）。
/// 仅向已存在的类别追加缺失的 ID（只多不少）。
fn apply_netease_extras(cfg: &Config, branch: &str, translator: &mut Translator<'_>) -> Result<()> {
    let dir = cfg.netease_extra_dir.join(branch);
    let Ok(entries) = std::fs::read_dir(&dir) else {
        return Ok(());
    };
    for entry in entries {
        let entry = entry?;
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }
        let Some(category) = path.file_stem().and_then(|e| e.to_str()) else {
            continue;
        };
        if !translator.results.contains_key(category) {
            tracing::warn!("[netease/{branch}] netease_extra 类别 {category} 不在生成结果中，已跳过");
            continue;
        }
        let value = load_jsonc_file(&path)?;
        let Value::Object(obj) = value else {
            bail!("{} 应为对象", path.display());
        };
        let map = translator.results.get_mut(category).unwrap();
        for (id, desc) in obj {
            if map.contains_key(&id) {
                continue; // 只多不少
            }
            let description = match desc {
                Value::String(s) if !s.is_empty() => s,
                _ => String::new(),
            };
            tracing::info!("[netease/{branch}] 叠加 {category} 多出 ID: {id}");
            map.insert(id, description);
        }
    }
    Ok(())
}

/// 组装某类别的四级翻译来源（全部持有所有权）。
fn build_levels(
    cfg: &Config,
    snapshot: &RepoSnapshot,
    category_file: &str,
    wiki: Option<&IndexMap<String, String>>,
) -> Result<CategoryLevels> {
    Ok(CategoryLevels {
        human: optional_object(&cfg.translations_dir.join(format!("{category_file}.json")))?,
        wiki: wiki.cloned(),
        caidlist: optional_object(&Path::new(snapshot.root()).join(format!("translation/{category_file}.json")))?,
        ai: optional_object(&cfg.ai_translations_dir.join(format!("{category_file}.json")))?,
    })
}

fn optional_object(path: &Path) -> Result<Option<serde_json::Map<String, Value>>> {
    if !path.exists() {
        return Ok(None);
    }
    let value = load_jsonc_file(path)?;
    match value {
        Value::Object(obj) => Ok(Some(obj)),
        _ => bail!("{} 应为对象", path.display()),
    }
}

fn load_jsonc_file(path: &Path) -> Result<Value> {
    let text = std::fs::read_to_string(path).with_context(|| format!("读取失败: {}", path.display()))?;
    crate::parse_jsonc(&text).with_context(|| format!("解析失败: {}", path.display()))
}
