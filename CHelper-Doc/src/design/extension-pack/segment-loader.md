# 段装载器（版本控制器）接口规格

> **状态**：已实现（`extension/MainPack.h/.cpp` 与 JNI open/readFile 落地，安卓主包链路已通；§4 正文即当前接口）｜供引擎 P0（合成器）实现时直接使用。
> 上游格式见 [pack-format.md](./pack-format.md) §9（分层主包）；装载结果交给 [composer.md](./composer.md) 的合成器。

## 1. 职责与范围

段装载器是引擎中**唯一感知主包分层结构**的模块。它把"主包 + 启用段"解析为"该段完整数据文件集合"，其余模块（合成器/CPack 装载/解析/补全）拿到的都是组装好的完整视图，**不感知分层**。

```
主包（.chepack zip | 目录 | 预编译 .cpack）
        │  MainPack::open
        ▼
┌──────────────────────────────┐
│  MainPack（只读驻留对象）      │  聚合 manifest + 分层索引
│  · listSegments()             │  （shared / <vt>/shared / <vt>/<branch>）
│  · loadSegment(vt, branch)    │
└──────────────┬───────────────┘
               │ 启用段配置（如 ["beta/vanilla"]）
               ▼
   SegmentData（文件集合：manifest.json + command/id/json/repeat 全量）
               │
               ▼
      Composer/CPackBuilder（装载 apply* → afterApply → 合成 CPack）
```

明确**不做**的事：段装载器不做命令/候选合并、不物化节点、不负责拓展包；它只回答"启用段长什么样"。

## 2. 输入与输出

| 项 | 说明 |
| --- | --- |
| 输入 1 | 主包源：`.chepack`（zip，由**平台层解压**后以 `Files` 传入）或分层目录（开发/调试，`Directory`）；预编译 `.cpack` 列 P3 |
| 输入 2 | 启用段：`<versionType>/<branch>`（六选一，如 `beta/vanilla`） |
| 输出 | `SegmentData`：该段完整数据的文件集合（`manifest.json` + `command/**` + `id/**` + `json/**` + `repeat/**`），文件内容与 `resources/<vt>/<branch>/` 逐文件一致（构建自检保证） |

分层组装规则（`pack-format.md` §9.2）：`shared/ ∪ versions/<vt>/shared/ ∪ versions/<vt>/<branch>/`，后层同名覆盖。

## 3. 装载流程

1. **打开** `MainPack::open(source)`：
   - `Files`：平台层已解压的文件集合（安卓 `ZipInputStream` 解包 `.chepack` 后传入）；
   - `Directory`：递归读取（开发/调试）；
   - 读取根 `manifest.json`，校验 `layout`（`"layered"` 当前；`"flat"` 兼容），建立分层索引；
2. **段清单** `listSegments()`：从聚合 manifest 的 `segments` 返回六段元数据（version/packId/name），供 UI 版本选择；
3. **组装** `loadSegment(vt, branch)`：三层合并（后层覆盖同名），返回 `SegmentData`；
   - 段 `manifest.json` 位于该段差异层，合并结果中必然存在；
4. **移交**：`SegmentData` 交给 Composer/CPackBuilder 按文件路径前缀分派装载（见 §8）。

## 4. C++ 接口草案（`CHelper::Extension`）

```cpp
namespace CHelper::Extension {

    // 一个包内文件：包内相对路径 + 内容（共享所有权，避免大文件拷贝）
    struct PackFile {
        std::string relPath;                        // "manifest.json" | "command/summon.json" | ...
        std::shared_ptr<const std::vector<uint8_t>> bytes;
    };

    // 启用段装载结果（该段完整数据）
    struct SegmentData {
        std::string segmentId;                      // "beta/vanilla"
        std::vector<PackFile> files;                // 已含 manifest.json
        size_t totalBytes() const;
    };

    // 主包源：Files（平台层已解压 zip / 外部预展开）或 Directory（分层目录，开发/调试）。
    // core 不内置 zip 解压：安卓由 Kotlin 用 ZipInputStream 解包后以 Files 传入；
    // 桌面/测试用 Directory 直接读分层目录。
    struct MainPackSource {
        enum class Kind { Files, Directory };
        Kind kind = Kind::Files;
        std::vector<PackFile> files;
        std::filesystem::path directory;
    };

    class MainPack {
    public:
        // 打开主包：解析聚合 manifest、校验 layout、建立分层索引；失败抛异常（带原因）
        static std::unique_ptr<MainPack> open(MainPackSource source);

        [[nodiscard]] const std::string &packId() const;
        [[nodiscard]] const std::string &layout() const;          // "layered" | "flat"

        // 供 UI 版本选择的段清单（来自聚合 manifest.segments）
        struct SegmentMeta { std::string id; std::string version; std::string packId; std::u16string name; };
        [[nodiscard]] std::vector<SegmentMeta> listSegments() const;

        // 组装启用段视图：shared → versions/<vt>/shared → versions/<vt>/<branch>
        // 段不存在/损坏 → 抛异常；每次调用返回独立结果（可并发）
        SegmentData loadSegment(std::string_view versionType, std::string_view branch) const;

    private:
        // 内部：解包字节常驻（shared_ptr），分层索引（rel → (layer, bytes)）
    };

    // 便捷：平台层已解包的文件集合 → 打开（安卓 Kotlin 解压 zip 后调用）
    inline std::unique_ptr<MainPack> openMainPack(std::vector<PackFile> files) {
        return MainPack::open(MainPackSource{MainPackSource::Kind::Files, std::move(files), {}});
    }
}
```

### 4.1 与合成器衔接的调用序列（P0 参考）

```cpp
// 1) 打开主包
auto mainPack = Extension::openMainPack(readFile("main-pack.chepack"));
// 2) UI 拿段清单
auto segments = mainPack->listSegments();               // ["beta/vanilla", ...]
// 3) 按当前版本选择装载启用段
Extension::SegmentData seg = mainPack->loadSegment("beta", "vanilla");
// 4) 交给合成器（见 composer.md）：段文件 + 已启用拓展包 → 合成 CPack
ComposeResult result = compose(std::move(seg.files), enabledExtensionPacks, options);
// 5) 版本切换：只重跑 3→4（MainPack 常驻，不需重新 open）
```

## 5. Android（JNI / Kotlin）落地形态

> 注：早期草案曾设想过 `open(zipBytes)` 直传字节并在 core 内解析 zip；落地改为**平台层解压后以文件集合传入**（安卓 `ZipInputStream`），与 §4 `Kind::Files` 一致（core 不内置 zip/miniz）。

JNI（`CHelperAndroid.cpp`，命名与 Kotlin 包装层对应）：

```cpp
// MainPack（入参为文件集合：相对路径数组 + 字节数组，平台层已解压 .chepack）
JNIEXPORT jlong JNICALL Java_yancey_chelper_core_MainPack_open0(
        JNIEnv *, jobject, jobjectArray relPaths, jobjectArray contents);
JNIEXPORT void   JNICALL Java_yancey_chelper_core_MainPack_release0(JNIEnv *, jobject, jlong ptr);
JNIEXPORT jobjectArray JNICALL Java_yancey_chelper_core_MainPack_listSegments0(JNIEnv *, jobject, jlong ptr);
JNIEXPORT jbyteArray JNICALL Java_yancey_chelper_core_MainPack_readFile0(
        JNIEnv *, jobject, jlong ptr, jstring versionType, jstring branch, jstring relPath);
// 合成（MainPack 常驻 + 启用段 + 拓展包文件集合 → 合成 core）
JNIEXPORT jlong JNICALL Java_yancey_chelper_core_CHelperCore_compose0(
        JNIEnv *, jobject, jlong mainPackPtr, jobjectArray enabledSegments,
        jobjectArray packRelPaths, jobjectArray packContents, jintArray packCounts);
```

Kotlin 形态（`core/MainPack.kt`、`core/CHelperCore.kt`、`core/MainPackProvider.kt` 已落地）：

```kotlin
object MainPackProvider {
    fun get(context: Context): MainPack?   // assets 打开 main-pack.chepack 一次，常驻；失败缓存返回 null
}
class MainPack private constructor() : Closeable {
    val segments: List<Segment>            // 版本选择 UI 数据源（含 name/version）
    fun readFile(versionType: String, branch: String, relPath: String): ByteArray?
    companion object {
        fun open(relPaths: Array<String>, contents: Array<ByteArray>): MainPack  // 平台层解压后传入
    }
}
object CHelperCore {
    fun compose(mainPack: MainPack, enabledSegments: Array<String>, extensionPacks: List<Map<String, ByteArray>>): CHelperCore
}
```

## 6. Web / Qt（暂不做，维持现状）

**暂不做**：Web/Qt 端继续加载旧的六份 `.cpack`，不实现主包/段装载/合成 API。若未来要做，再按本文档补 wasm/桌面导出（形态与 §4/§5 对称：文件集合 `open` + `listSegments()` + `compose(mainPack, enabledSegments, packs)`，zip 由平台层解压）。

## 7. 生命周期 / 线程 / 错误处理

| 项 | 约定 |
| --- | --- |
| 常驻 | `MainPack` 打开一次、长期驻留（解包字节常驻内存）；版本切换/包集合变化**不重新 open**，只重跑 `loadSegment` + compose |
| 只读/线程 | `MainPack` 只读；`loadSegment` 每次返回独立结果，可多线程并发调用（内部索引只读） |
| 所有权 | `SegmentData.files` 的 `bytes` 用 `shared_ptr` 引用主包解包内存；合成器装载时把内容复制进合成 CPack 后，主包是否释放不影响合成 CPack（与现有 shared_ptr<const CPack> 纪律一致） |
| 错误 | 打开失败（坏 zip/缺 manifest/layout 非法）、段不存在、段差异层引用缺失 → 抛异常带原因；上层整体拒绝，不产生半成品 |
| 幂等 | `loadSegment` 无副作用；同一输入返回等价结果 |
| 自检 | 主包**构建期**已做全量装载自检（merge vs 源 hash 一致）；运行期 `loadSegment` 无需重复校验（信任包），但可加调试断言 |

## 8. 与现有代码的衔接点（P0 改动清单）

| 位置 | 改动 |
| --- | --- |
| `resources/CPack.h/.cpp` | 把"装载（applyId/applyJson/applyRepeat/applyCommand）+ 物化（afterApply）"从构造拆成可累积 **CPackBuilder**（保持三个现有构造入口行为不变）；新增按 `PackFile` 集合装载的入口（按 relPath 前缀分派：`command/`→applyCommand 等，`manifest.json`→manifest） |
| 新增 `extension/MainPack.h/.cpp` | 本规格 §4 实现；**不内置 zip 解压**——zip 由平台层（安卓 `ZipInputStream`）解压后以 `Files` 传入，桌面/测试用 `Directory` |
| `extension/Composer` | 输入：`SegmentData`（或直接 files）+ 启用拓展包；输出合成 CPack（见 composer.md） |
| 生成器/校验 | `tools/build_main_pack.mjs` 已产出分层主包并自检；运行期不需要再造包 |

## 9. 测试要点（并入 clients-and-roadmap §5）

1. 六段分别 `loadSegment` → 文件集合与 `resources/<vt>/<branch>/` 逐文件 hash 一致（等价于构建自检的运行期复验，抽样即可）；
2. 版本切换：同一 `MainPack` 依次 load 两段 → compose → 行为与各自分支一致；
3. `layout: "flat"`（非分层）兼容路径（若未来主包退化为全量段）；
4. 坏 zip/缺 manifest/段缺失 → 拒绝且不影响已驻留 core；
5. 并发：多线程同时 `loadSegment` 无竞态；
6. JNI（安卓）：字节流 open + 段清单 + compose 冒烟。

## 10. 与 Composer API 的关系（一句话）

`composer.md` §5 的 `compose(mainPackPath, enabledSegments, packs)` 落地时，`mainPackPath` 由 `MainPack::open` + `loadSegment` 取代为"段视图文件集合"，签名保持不变、实现分层在段装载器内部完成。
