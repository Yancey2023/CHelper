use super::{file_index, MediaLinks};
use crate::cache::HttpCache;
use anyhow::{Context, Result};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet};
use std::sync::Arc;
use std::time::Duration;

pub(super) type SoundPools = BTreeMap<String, BTreeSet<String>>;

// These Wiki file titles were verified against the waveforms of Mojang's Bedrock samples.
// Use the asset path so the alias follows events that actually include the sample in this version.
const WIKI_SOUND_ALIASES: &[(&str, &str)] = &[
    ("block/copper_chest/open1", "Copper chest open.wav"),
    (
        "block/creaking_heart/ambient1",
        "Block.creaking heart.idle1.wav",
    ),
    (
        "block/pale_hanging_moss/ambient10",
        "Pale Hanging Moss idle10.ogg",
    ),
    ("block/sand/sand12", "Sand idle12.ogg"),
    ("block/shelf/break/break1", "Chiseled Bookshelf break1.ogg"),
    ("block/sulfur_spike/break1", "Sulfur break1.wav"),
    ("block/sulfur_spike/place1", "Sulfur place1.wav"),
    (
        "block/trial_spawner/charge_activate",
        "Trial Spawner ominous activate.ogg",
    ),
    ("entity/bobber/castfast", "Fishing rod cast.ogg"),
    ("event/mob_effects/bad_omen", "Event bad omen.ogg"),
    ("event/mob_effects/raid_omen", "Event raid omen.ogg"),
    ("event/mob_effects/trial_omen", "Event trial omen.ogg"),
    ("lead/break", "Leash Knot break.ogg"),
    ("liquid/splash", "Water splash1.ogg"),
    ("mob/baby_nautilus/swim2", "Mob.nautilus.swim2.wav"),
    (
        "mob/copper_golem/regular/become_statue1",
        "Copper golem become statue1 JE1.ogg",
    ),
    ("mob/llama/carpet_unequip", "Llama unequip.ogg"),
    ("mob/player/attack/crit3", "Critical attack3.ogg"),
    ("mob/shulker/hurt_close1", "Mob.shulker.hurt closed1.wav"),
];

pub(super) async fn fetch_definitions(
    http: &HttpCache,
    reference: &str,
    ttl: Duration,
) -> Result<SoundPools> {
    let url = format!("https://raw.githubusercontent.com/Mojang/bedrock-samples/{reference}/resource_pack/sounds/sound_definitions.json");
    let doc = http.get_json(&url, ttl).await?;
    let definitions = doc["sound_definitions"]
        .as_object()
        .context("基岩版声音定义缺少 sound_definitions")?;
    Ok(definitions
        .iter()
        .filter_map(|(id, event)| {
            let assets: BTreeSet<_> = event["sounds"]
                .as_array()?
                .iter()
                .filter_map(|sound| {
                    sound
                        .as_str()
                        .or_else(|| sound.get("name").and_then(Value::as_str))
                })
                .filter(|path| path.starts_with("sounds/"))
                .map(str::to_owned)
                .collect();
            (!assets.is_empty()).then_some((id.clone(), assets))
        })
        .collect())
}

pub(super) fn version_tag(edition: &str, version: &str) -> Option<String> {
    let version = version
        .split('.')
        .map(|part| part.parse::<u32>().ok().map(|value| value.to_string()))
        .collect::<Option<Vec<_>>>()?
        .join(".");
    let suffix = if edition == "beta" { "-preview" } else { "" };
    Some(format!("v{version}{suffix}"))
}

pub(super) fn fill_missing(
    events: &mut MediaLinks,
    files: &BTreeSet<String>,
    definitions: &SoundPools,
) -> usize {
    let paths_by_stem = paths_by_stem(definitions);

    let mut links_by_asset = BTreeMap::new();
    for url in files {
        let Some((name, _)) = url
            .rsplit('/')
            .next()
            .and_then(|name| name.rsplit_once('.'))
        else {
            continue;
        };
        if name.contains('%') {
            continue;
        }
        if let Some(paths) = paths_by_stem.get(&normalize(name)) {
            if paths.len() == 1 {
                links_by_asset.entry(*paths.first().unwrap()).or_insert(url);
            }
        }
    }

    let mut direct_by_pool = BTreeMap::<_, BTreeSet<&String>>::new();
    for (event, url) in events.iter() {
        if let Some(assets) = definitions.get(event) {
            direct_by_pool.entry(assets).or_default().insert(url);
            if assets.len() == 1 {
                let asset = assets.first().unwrap().as_str();
                if normalize(event) == normalize(asset.trim_start_matches("sounds/")) {
                    links_by_asset.entry(asset).or_insert(url);
                }
            }
        }
    }

    let mut additions = MediaLinks::new();
    for (event, assets) in definitions {
        if events.contains_key(event) {
            continue;
        }
        let shared_file = assets
            .iter()
            .find_map(|asset| links_by_asset.get(asset.as_str()));
        let common_sample = direct_by_pool
            .get(assets)
            .filter(|urls| urls.len() == 1)
            .and_then(|urls| urls.first());
        if let Some(&url) = shared_file.or(common_sample) {
            additions.insert(event.clone(), url.clone());
        }
    }
    let added = additions.len();
    events.extend(additions);
    added
}

pub(super) async fn find_published_sounds(
    http: &Arc<HttpCache>,
    ids: &BTreeSet<String>,
    existing: &MediaLinks,
    definitions: &SoundPools,
    ttl: Duration,
) -> Result<MediaLinks> {
    let missing: Vec<_> = ids
        .iter()
        .filter(|id| !existing.contains_key(*id))
        .collect();
    let unindexed_assets: BTreeSet<_> = missing
        .iter()
        .filter_map(|id| definitions.get(*id))
        .flatten()
        .collect();
    let stems = paths_by_stem(definitions);
    let mut candidates = BTreeMap::new();
    for asset in unindexed_assets {
        for shape in asset_shapes(asset) {
            let Some(paths) = stems.get(&normalize(&shape.join(""))) else {
                continue;
            };
            if paths.len() != 1 {
                continue;
            }
            for separator in [".", "_"] {
                for extension in [".wav", ".ogg"] {
                    let title = format!("File:{}{extension}", shape.join(separator));
                    candidates.insert(title, asset.as_str());
                }
            }
        }
    }
    resolve_candidates(http, &missing, definitions, candidates, ttl).await
}

pub(super) async fn find_indexed_sounds(
    http: &Arc<HttpCache>,
    ids: &BTreeSet<String>,
    existing: &MediaLinks,
    definitions: &SoundPools,
    index: &BTreeSet<String>,
    ttl: Duration,
) -> Result<MediaLinks> {
    let missing: Vec<_> = ids
        .iter()
        .filter(|id| !existing.contains_key(*id))
        .collect();
    let assets: BTreeSet<_> = missing
        .iter()
        .filter_map(|id| definitions.get(*id))
        .flatten()
        .map(String::as_str)
        .collect();
    let stems = paths_by_stem(definitions);
    let mut candidates = BTreeMap::new();
    for title in index {
        let Some((stem, _)) = title
            .strip_prefix("File:")
            .and_then(|file| file.rsplit_once('.'))
        else {
            continue;
        };
        let Some(paths) = stems.get(&normalize(stem)).filter(|paths| paths.len() == 1) else {
            continue;
        };
        if let Some(&asset) = paths.first().filter(|asset| assets.contains(*asset)) {
            candidates.insert(title.clone(), asset);
        }
    }
    let mut found = resolve_candidates(http, &missing, definitions, candidates, ttl).await?;
    let aliases = WIKI_SOUND_ALIASES
        .iter()
        .copied()
        .collect::<BTreeMap<_, _>>();
    let missing: Vec<_> = missing
        .into_iter()
        .filter(|id| !found.contains_key(*id))
        .collect();
    let candidates = missing
        .iter()
        .filter_map(|id| definitions.get(*id))
        .flatten()
        .filter_map(|asset| {
            let title = aliases.get(asset.strip_prefix("sounds/")?)?;
            Some((format!("File:{title}"), asset.as_str()))
        })
        .collect();
    found.extend(resolve_candidates(http, &missing, definitions, candidates, ttl).await?);
    Ok(found)
}

async fn resolve_candidates(
    http: &Arc<HttpCache>,
    missing: &[&String],
    definitions: &SoundPools,
    candidates: BTreeMap<String, &str>,
    ttl: Duration,
) -> Result<MediaLinks> {
    let files = file_index::find_files(http, candidates.keys().cloned().collect(), ttl).await?;
    let mut links_by_asset = BTreeMap::new();
    for (title, info) in files {
        if let Some(url) = file_index::sound_url(&info) {
            links_by_asset.entry(candidates[&title]).or_insert(url);
        }
    }
    Ok(missing
        .iter()
        .copied()
        .filter_map(|id| {
            let assets = definitions.get(id)?;
            let url = assets
                .iter()
                .find_map(|asset| links_by_asset.get(asset.as_str()))?;
            Some((id.clone(), url.clone()))
        })
        .collect())
}

fn paths_by_stem(definitions: &SoundPools) -> BTreeMap<String, BTreeSet<&str>> {
    let mut paths_by_stem = BTreeMap::<_, BTreeSet<_>>::new();
    let known_assets: BTreeSet<_> = definitions.values().flatten().collect();
    for asset in known_assets {
        for shape in asset_shapes(asset) {
            let stem = normalize(&shape.join(""));
            if stem.len() >= 6 {
                paths_by_stem
                    .entry(stem)
                    .or_default()
                    .insert(asset.as_str());
            }
        }
    }
    paths_by_stem
}

fn asset_shapes(asset: &str) -> Vec<Vec<&str>> {
    let parts: Vec<_> = asset.trim_start_matches("sounds/").split('/').collect();
    let mut shapes = Vec::new();
    for suffix in (0..parts.len()).map(|start| &parts[start..]) {
        shapes.push(suffix.to_vec());
        // The wiki sometimes omits a folder already repeated in the filename, e.g. shelf/activate/activate1.
        if let [parents @ .., folder, file] = suffix {
            if file.starts_with(folder) {
                let mut collapsed = parents.to_vec();
                collapsed.push(file);
                shapes.push(collapsed);
            }
        }
    }
    shapes
}

fn normalize(name: &str) -> String {
    name.bytes()
        .filter(u8::is_ascii_alphanumeric)
        .map(|ch| char::from(ch.to_ascii_lowercase()))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn uses_the_published_preview_and_normalized_release_tags() {
        assert_eq!(
            version_tag("beta", "1.26.70.20").as_deref(),
            Some("v1.26.70.20-preview")
        );
        assert_eq!(
            version_tag("netease", "1.21.50.07").as_deref(),
            Some("v1.21.50.7")
        );
    }

    #[test]
    fn fills_shared_and_unambiguously_named_assets_without_overwriting_direct_links() {
        let click = "https://zh.minecraft.wiki/images/Click.ogg";
        let shelf = "https://zh.minecraft.wiki/images/Shelf_activate1.ogg";
        let ambiguous = "https://zh.minecraft.wiki/images/Impact1.ogg";
        let mut events = MediaLinks::from([("random.click".into(), click.into())]);
        let files = [shelf, ambiguous].into_iter().map(str::to_owned).collect();
        let pool = |asset: &str| BTreeSet::from([asset.to_owned()]);
        let definitions = BTreeMap::from([
            ("random.click".into(), pool("sounds/random/click")),
            ("block.click".into(), pool("sounds/random/click")),
            (
                "block.shelf.activate".into(),
                pool("sounds/block/shelf/activate/activate1"),
            ),
            ("block.other.hit".into(), pool("sounds/block/other/impact1")),
            ("mob.other.hit".into(), pool("sounds/mob/other/impact1")),
        ]);
        assert_eq!(fill_missing(&mut events, &files, &definitions), 2);
        assert_eq!(events["random.click"], click);
        assert_eq!(events["block.click"], click);
        assert_eq!(events["block.shelf.activate"], shelf);
        assert!(!events.contains_key("block.other.hit"));
    }
}
