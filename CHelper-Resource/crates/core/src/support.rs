//! 版本特性开关（移植 caidlist `src/sources/support.js` 中 chelper 相关的部分）。
//!
//! 只保留输出 chelper 资源包时判定"类别是否存在"所需的开关。

/// 比较两个基岩版版本号（`*` 视为无穷大，非数字段视为 -1）。
pub fn compare_minecraft_version(a: &str, b: &str) -> i64 {
    let parse = |s: &str| -> Vec<i64> {
        s.split('.')
            .map(|part| {
                if part == "*" {
                    i64::MAX
                } else {
                    part.parse::<i64>().unwrap_or(-1)
                }
            })
            .collect()
    };
    let aver = parse(a);
    let bver = parse(b);
    for i in 0..aver.len().min(bver.len()) {
        if aver[i] != bver[i] {
            return aver[i] - bver[i];
        }
    }
    (aver.len() - bver.len()) as i64
}

fn in_range(version: &str, lower: &str, upper: &str) -> bool {
    compare_minecraft_version(version, lower) >= 0 && compare_minecraft_version(version, upper) <= 0
}

pub fn loot_table(core_version: &str) -> bool {
    in_range(core_version, "1.18.0.21", "*") || in_range(core_version, "1.18.0.02", "1.18.0.02")
}

pub fn damage_command(core_version: &str) -> bool {
    in_range(core_version, "1.18.10.26", "*") || in_range(core_version, "1.18.10.04", "1.18.10.04")
}

pub fn placefeature_command(core_version: &str) -> bool {
    in_range(core_version, "1.18.20.25", "1.18.20.26")
}

pub fn new_locate_command(core_version: &str) -> bool {
    in_range(core_version, "1.19.10.23", "*") || in_range(core_version, "1.19.10.03", "1.19.10.03")
}

pub fn inputpermission_command(core_version: &str) -> bool {
    in_range(core_version, "1.19.80.21", "*") || in_range(core_version, "1.19.80.02", "1.19.80.02")
}

pub fn camera_command(core_version: &str, branch: &str) -> bool {
    if branch == "experiment" {
        return in_range(core_version, "1.20.0.22", "*") || in_range(core_version, "1.20.0.01", "1.20.0.01");
    }
    in_range(core_version, "1.20.20.22", "*")
}

pub fn recipe_new_command(core_version: &str, branch: &str) -> bool {
    if branch == "experiment" {
        return in_range(core_version, "1.20.20.20", "*");
    }
    if branch == "education" {
        return in_range(core_version, "1.20.40.23", "1.20.40.23") || in_range(core_version, "1.20.50.03", "*");
    }
    in_range(core_version, "1.20.20.21", "*")
}

pub fn hud_command(core_version: &str, branch: &str) -> bool {
    if branch == "experiment" {
        return in_range(core_version, "1.20.60.23", "*") || in_range(core_version, "1.20.60.04", "1.20.60.04");
    }
    in_range(core_version, "1.20.80.23", "*") || in_range(core_version, "1.20.80.05", "1.20.80.05")
}

pub fn place_command_feature_sub_command(core_version: &str, branch: &str) -> bool {
    if branch == "experiment" {
        return in_range(core_version, "1.21.60.23", "*") || in_range(core_version, "1.21.60.10", "1.21.60.10");
    }
    in_range(core_version, "1.21.70.22", "*") || in_range(core_version, "1.21.70.03", "1.21.70.03")
}

pub fn control_scheme_command(core_version: &str, branch: &str) -> bool {
    if branch == "experiment" {
        return in_range(core_version, "1.21.80.27", "*") || in_range(core_version, "1.21.80.3", "1.21.80.3");
    }
    in_range(core_version, "1.21.90.23", "*") || in_range(core_version, "1.21.90.3", "1.21.90.3")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_compare() {
        assert_eq!(compare_minecraft_version("1.26.52.3", "1.26.60.29"), -8);
        assert_eq!(compare_minecraft_version("1.21.90.3", "1.21.90.3"), 0);
        assert!(in_range("1.26.52.3", "1.20.20.22", "*"));
        assert!(!control_scheme_command("1.21.50.7", "vanilla"));
        assert!(control_scheme_command("1.26.52.3", "vanilla"));
    }
}
