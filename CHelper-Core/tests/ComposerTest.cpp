/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Yancey
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include <chelper/CHelperCore.h>
#include <chelper/extension/Composer.h>
#include <chelper/extension/MainPack.h>
#include <gtest/gtest.h>
#include <optional>

namespace CHelper::Test {

    namespace {

        std::unique_ptr<Extension::MainPack> openMainPack() {
            std::filesystem::path resourceDir(RESOURCE_DIR);
            Extension::MainPackSource source;
            source.kind = Extension::MainPackSource::Kind::Directory;
            source.directory = resourceDir / "main-pack";
            return Extension::MainPack::open(std::move(source));
        }

        Extension::SegmentData betaVanilla() {
            auto pack = openMainPack();
            return pack->loadSegment("beta", "vanilla");
        }

        // 把目录读成拓展包文件集合（模拟平台层解压 zip 后的 Files）
        Extension::ExtensionPackData packFromDir(const std::filesystem::path &dir) {
            Extension::ExtensionPackData pack;
            for (std::filesystem::recursive_directory_iterator it(dir), end; it != end; ++it) {
                if (!it->is_regular_file()) {
                    continue;
                }
                std::ifstream in(it->path(), std::ios::binary);
                auto bytes = std::make_shared<std::vector<uint8_t>>(
                        (std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
                pack.files.push_back({std::filesystem::relative(it->path(), dir).generic_string(), bytes});
            }
            return pack;
        }

        bool hasSuggestion(const CommandContext &context, const std::u16string &text) {
            // 补全位置 = 当前命令文本末尾
            const size_t index = context.getCommand().size();
            for (const auto &s: context.getSuggestions(index)) {
                if (s.content->name == text) {
                    return true;
                }
            }
            return false;
        }

        // 建议里命中任一候选即返回命中的那个（用于"名称可能带 minecraft: 前缀"的表）
        std::optional<std::u16string> suggestionAmong(const CommandContext &context,
                                                      std::initializer_list<std::u16string> names) {
            const size_t index = context.getCommand().size();
            for (const auto &s: context.getSuggestions(index)) {
                for (const auto &n: names) {
                    if (s.content->name == n) {
                        return n;
                    }
                }
            }
            return std::nullopt;
        }

    }// namespace

    TEST(ComposerTest, NoExtensionPackEqualsBaseline) {
        auto result = Extension::compose(betaVanilla(), {});
        ASSERT_NE(result.cpack, nullptr);
        auto core = CHelperCore(result.cpack);
        const std::u16string giveCmd = u"/give @s stone";
        std::unique_ptr<CommandContext> ctx(core.createContext(giveCmd));
        ASSERT_NE(ctx, nullptr);
        EXPECT_GT(ctx->getSuggestions(giveCmd.size()).size(), 0u);
    }

    TEST(ComposerTest, FilesSourceWithBackslashRelEqualsAndroidPath) {
        // 模拟安卓平台层 zip 解压后的 Files 源：relPath 用反斜杠（与 main-pack.chepack 条目一致），
        // 装载 release/experiment 段并合成，验证与目录源等价（命令补全可用）。
        const std::filesystem::path dir = std::filesystem::path(RESOURCE_DIR) / "main-pack";
        CHelper::Extension::MainPackSource source;
        source.kind = CHelper::Extension::MainPackSource::Kind::Files;
        for (std::filesystem::recursive_directory_iterator it(dir), end; it != end; ++it) {
            if (!it->is_regular_file()) {
                continue;
            }
            std::string rel = std::filesystem::relative(it->path(), dir).generic_string();
            for (char &ch: rel) {
                if (ch == '/') {
                    ch = '\\';
                }
            }
            std::ifstream in(it->path(), std::ios::binary);
            auto bytes = std::make_shared<std::vector<uint8_t>>(
                    (std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
            source.files.push_back({rel, std::move(bytes)});
        }
        auto pack = CHelper::Extension::MainPack::open(std::move(source));
        // 恶意/异常条目（zip 目录条目、空名）必须在 open 阶段被清理，不能进分层索引
        {
            CHelper::Extension::MainPackSource junk;
            junk.kind = CHelper::Extension::MainPackSource::Kind::Files;
            auto emptyBytes = std::make_shared<std::vector<uint8_t>>();
            junk.files.push_back({"versions\\release\\", emptyBytes});
            junk.files.push_back({"shared\\", emptyBytes});
            junk.files.push_back({"", emptyBytes});
            junk.files.push_back({"/", emptyBytes});
            auto junkPack = CHelper::Extension::MainPack::open(std::move(junk));
            EXPECT_EQ(junkPack->listSegments().size(), 0u); // 无 manifest → 无段
            EXPECT_THROW(junkPack->loadSegment("release", "vanilla"), std::exception);
        }
        auto seg = pack->loadSegment("release", "experiment");
        auto result = CHelper::Extension::compose(seg, {});
        ASSERT_NE(result.cpack, nullptr);
        auto core = CHelperCore(result.cpack);
        const std::u16string giveCmd = u"/give @s stone";
        std::unique_ptr<CommandContext> ctx(core.createContext(giveCmd));
        ASSERT_NE(ctx, nullptr);
        EXPECT_GT(ctx->getSuggestions(giveCmd.size()).size(), 0u);
    }

    TEST(ComposerTest, TemplatePackWithCommentsComposes) {
        // 模板包（templates/extension-pack）全部 JSON 内联注释（// 与块注释），
        // 引擎必须自动忽略注释后正常装载——此用例同时守护"JSON 注释支持"不回归。
        std::filesystem::path resourceDir(RESOURCE_DIR);
        Extension::ExtensionPackData tpl = packFromDir(resourceDir / "templates" / "extension-pack");
        auto result = Extension::compose(betaVanilla(), {std::move(tpl)});
        ASSERT_NE(result.cpack, nullptr);
        auto core = CHelperCore(result.cpack);

        // /welcome <候选表参数>：应为 greetWho 的候选；完整命令无错误
        {
            const std::u16string cmd = u"/welcome ";
            std::unique_ptr<CommandContext> ctx(core.createContext(cmd));
            ASSERT_NE(ctx, nullptr);
            EXPECT_TRUE(hasSuggestion(*ctx, u"player"));
            EXPECT_TRUE(hasSuggestion(*ctx, u"vip"));
            EXPECT_TRUE(hasSuggestion(*ctx, u"admin"));
            const std::u16string full = u"/welcome admin";
            std::unique_ptr<CommandContext> ctx2(core.createContext(full));
            ASSERT_NE(ctx2, nullptr);
            EXPECT_TRUE(ctx2->getErrorReasons().empty());
        }
        // 别名 /hi 应可用
        {
            const std::u16string cmd = u"/hi admin";
            std::unique_ptr<CommandContext> ctx(core.createContext(cmd));
            ASSERT_NE(ctx, nullptr);
            EXPECT_TRUE(ctx->getErrorReasons().empty());
        }
        // /grant <目标选择器>：合法选择器无错误
        {
            const std::u16string cmd = u"/grant @a[type=player]";
            std::unique_ptr<CommandContext> ctx(core.createContext(cmd));
            ASSERT_NE(ctx, nullptr);
            EXPECT_TRUE(ctx->getErrorReasons().empty());
        }
        // execute 扩展：/execute myaction @s 可用
        {
            const std::u16string cmd = u"/execute myaction @s";
            std::unique_ptr<CommandContext> ctx(core.createContext(cmd));
            ASSERT_NE(ctx, nullptr);
            EXPECT_TRUE(ctx->getErrorReasons().empty());
        }
    }

    TEST(ComposerTest, DemoPackCommandsAndSources) {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        Extension::ExtensionPackData demo = packFromDir(resourceDir / "examples" / "demo-server-pack");
        auto result = Extension::compose(betaVanilla(), {std::move(demo)});
        ASSERT_NE(result.cpack, nullptr);

        // 命令来源：kit 来自示例包，give 来自内置
        auto kitIt = result.commandSources.find("kit");
        ASSERT_NE(kitIt, result.commandSources.end());
        EXPECT_EQ(utf8::utf16to8(kitIt->second), "Demo 服务器命令包");
        EXPECT_EQ(result.commandSources["give"], u"");

        auto core = CHelperCore(result.cpack);
        std::unique_ptr<CommandContext> ctx(core.createContext(u"/kit "));
        ASSERT_NE(ctx, nullptr);
        EXPECT_TRUE(hasSuggestion(*ctx, u"starter"));
        EXPECT_TRUE(hasSuggestion(*ctx, u"diamond"));

        std::unique_ptr<CommandContext> hub(core.createContext(u"/hub"));
        ASSERT_NE(hub, nullptr);
        EXPECT_TRUE(hub->getErrorReasons().empty());
    }

    TEST(ComposerTest, ConflictingCommandOverridden) {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        Extension::ExtensionPackData demo = packFromDir(resourceDir / "examples" / "demo-server-pack");
        auto result = Extension::compose(betaVanilla(), {std::move(demo)});
        // demo 包不含与内置同名的命令，此处验证主包优先（kit 未被内置覆盖，正常注册）
        EXPECT_TRUE(result.overriddenCommands.empty());
        EXPECT_NE(result.cpack, nullptr);
    }

    TEST(ComposerTest, ImportedTestPacksCompose) {
        // 复现"导入测试包后合成失败（invalid extension fragment）"：直接用发给安卓的
        // test-packs/pack-a（与 pack-b）目录文件集合走同一合成路径。目录不存在时跳过（本地验收夹具）。
        std::filesystem::path resourceDir(RESOURCE_DIR);
        const auto packA = resourceDir / "test-packs" / "pack-a";
        const auto packB = resourceDir / "test-packs" / "pack-b";
        if (!std::filesystem::exists(packA) || !std::filesystem::exists(packB)) {
            GTEST_SKIP() << "test-packs 目录不存在（仅本地验收夹具）";
        }
        // 单包 A：全功能（含带注释的 execute 扩展片段）
        {
            Extension::ExtensionPackData a = packFromDir(packA);
            auto result = Extension::compose(betaVanilla(), {std::move(a)});
            ASSERT_NE(result.cpack, nullptr);
            auto core = CHelperCore(result.cpack);
            const std::u16string welcome = u"/welcome ";
            std::unique_ptr<CommandContext> ctx(core.createContext(welcome));
            ASSERT_NE(ctx, nullptr);
            EXPECT_TRUE(hasSuggestion(*ctx, u"all"));
            EXPECT_TRUE(hasSuggestion(*ctx, u"player"));
            std::unique_ptr<CommandContext> exec(core.createContext(u"/execute myaction @s"));
            ASSERT_NE(exec, nullptr);
            EXPECT_TRUE(exec->getErrorReasons().empty());
            // 内置实体表引用：/pentity 建议含 zombie（可能带 minecraft: 前缀），整条命令无错误
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/pentity "));
                ASSERT_NE(e, nullptr);
                auto ent = suggestionAmong(*e, {u"zombie", u"minecraft:zombie"});
                ASSERT_TRUE(ent.has_value());
                std::unique_ptr<CommandContext> full(core.createContext(u"/pentity " + *ent));
                ASSERT_NE(full, nullptr);
                EXPECT_TRUE(full->getErrorReasons().empty());
            }
            // 内置物品大表引用：/pitem 建议含 stone（可能带前缀），整条命令无错误
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/pitem "));
                ASSERT_NE(e, nullptr);
                auto item = suggestionAmong(*e, {u"stone", u"minecraft:stone"});
                ASSERT_TRUE(item.has_value());
                std::unique_ptr<CommandContext> full(core.createContext(u"/pitem " + *item));
                ASSERT_NE(full, nullptr);
                EXPECT_TRUE(full->getErrorReasons().empty());
            }
            // 内置方块大表引用：/pblock 建议含 stone（可能带前缀），整条命令无错误
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/pblock "));
                ASSERT_NE(e, nullptr);
                auto block = suggestionAmong(*e, {u"stone", u"minecraft:stone"});
                ASSERT_TRUE(block.has_value());
                std::unique_ptr<CommandContext> full(core.createContext(u"/pblock " + *block));
                ASSERT_NE(full, nullptr);
                EXPECT_TRUE(full->getErrorReasons().empty());
                // minecraft: 前缀长度修正（std::size 不再含 \0）：输入完整前缀应出带前缀候选
                std::unique_ptr<CommandContext> pref(core.createContext(u"/pblock minecraft:"));
                ASSERT_NE(pref, nullptr);
                EXPECT_TRUE(hasSuggestion(*pref, u"minecraft:stone"));
            }
            // 拓展包自带大表合并：自定义方块/物品并入主包表，建议可补全、可解析
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/pblock "));
                ASSERT_NE(e, nullptr);
                auto custom = suggestionAmong(*e, {u"demo_machine", u"demo:demo_machine"});
                ASSERT_TRUE(custom.has_value());
                std::unique_ptr<CommandContext> full(core.createContext(u"/pblock " + *custom));
                ASSERT_NE(full, nullptr);
                EXPECT_TRUE(full->getErrorReasons().empty());
                // 自定义方块：必须带命名空间前缀（demo:demo_machine）；短名 demo_machine 报错
                std::unique_ptr<CommandContext> pref(core.createContext(u"/pblock demo:demo_machine"));
                ASSERT_NE(pref, nullptr);
                EXPECT_TRUE(pref->getErrorReasons().empty());
                std::unique_ptr<CommandContext> plain(core.createContext(u"/pblock demo_machine"));
                ASSERT_NE(plain, nullptr);
                EXPECT_FALSE(plain->getErrorReasons().empty());
                // 自定义方块属性状态带描述：前缀形式 /setblock ... demo:demo_machine[lit=false] 无错误
                std::unique_ptr<CommandContext> states(core.createContext(u"/setblock ~ ~ ~ demo:demo_machine[lit=false]"));
                ASSERT_NE(states, nullptr);
                EXPECT_TRUE(states->getErrorReasons().empty());
                std::unique_ptr<CommandContext> statesPlain(core.createContext(u"/setblock ~ ~ ~ demo_machine[lit=false]"));
                ASSERT_NE(statesPlain, nullptr);
                EXPECT_FALSE(statesPlain->getErrorReasons().empty());
                // 真机验证等价路径：前缀不带状态通过；/give 自定义物品（默认命名空间短名合法）
                {
                    std::unique_ptr<CommandContext> s1(core.createContext(u"/setblock ~ ~ ~ demo:demo_machine"));
                    ASSERT_NE(s1, nullptr);
                    EXPECT_TRUE(s1->getErrorReasons().empty());
                    std::unique_ptr<CommandContext> s2(core.createContext(u"/setblock ~ ~ ~ demo_machine"));
                    ASSERT_NE(s2, nullptr);
                    EXPECT_FALSE(s2->getErrorReasons().empty());
                    std::unique_ptr<CommandContext> g1(core.createContext(u"/give @s custom_gadget"));
                    ASSERT_NE(g1, nullptr);
                    EXPECT_TRUE(g1->getErrorReasons().empty());
                }
                // 带命名空间候选（demo:demo_machine）必须继承来源徽标（NamespaceId::getIdWithNamespace 修复）
                {
                    std::unique_ptr<CommandContext> ns(core.createContext(u"/pblock demo:"));
                    ASSERT_NE(ns, nullptr);
                    bool found = false;
                    for (const auto &s: ns->getSuggestions(ns->getCommand().size())) {
                        if (s.content->name == u"demo:demo_machine") {
                            found = true;
                            EXPECT_EQ(s.content->packName.value_or(u""), u"测试资源包 A");
                        }
                    }
                    EXPECT_TRUE(found);
                }
            }
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/pitem "));
                ASSERT_NE(e, nullptr);
                auto custom = suggestionAmong(*e, {u"custom_gadget"});
                ASSERT_TRUE(custom.has_value());
                std::unique_ptr<CommandContext> full(core.createContext(u"/pitem " + *custom));
                ASSERT_NE(full, nullptr);
                EXPECT_TRUE(full->getErrorReasons().empty());
            }
            // 自定义实体（namespace 追加）：默认命名空间短名可用；demo 命名空间必须带前缀
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/pentity demo_guard"));
                ASSERT_NE(e, nullptr);
                EXPECT_TRUE(e->getErrorReasons().empty()); // 默认命名空间，短名合法
                std::unique_ptr<CommandContext> pet(core.createContext(u"/pentity demo:demo_pet"));
                ASSERT_NE(pet, nullptr);
                EXPECT_TRUE(pet->getErrorReasons().empty()); // 带前缀合法
                std::unique_ptr<CommandContext> petPlain(core.createContext(u"/pentity demo_pet"));
                ASSERT_NE(petPlain, nullptr);
                EXPECT_FALSE(petPlain->getErrorReasons().empty()); // demo 命名空间短名报错
                // demo: 前缀下出带前缀候选且继承来源徽标
                std::unique_ptr<CommandContext> sel(core.createContext(u"/pentity demo:"));
                ASSERT_NE(sel, nullptr);
                bool found = false;
                for (const auto &s: sel->getSuggestions(sel->getCommand().size())) {
                    if (s.content->name == u"demo:demo_pet") {
                        found = true;
                        EXPECT_EQ(s.content->packName.value_or(u""), u"测试资源包 A");
                    }
                }
                EXPECT_TRUE(found);
            }
            // 内置 normal 表引用：/pgamemode survival 建议与整条无错误
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/pgamemode "));
                ASSERT_NE(e, nullptr);
                EXPECT_TRUE(hasSuggestion(*e, u"survival"));
                std::unique_ptr<CommandContext> full(core.createContext(u"/pgamemode survival"));
                ASSERT_NE(full, nullptr);
                EXPECT_TRUE(full->getErrorReasons().empty());
            }
            // 包内 JSON 结构参数：/pjson {"text":"hi"} 无错误
            {
                std::unique_ptr<CommandContext> full(core.createContext(u"/pjson {\"text\":\"hi\"}"));
                ASSERT_NE(full, nullptr);
                EXPECT_TRUE(full->getErrorReasons().empty());
            }
            // 选择器参数：括号内参数（@a[type=…]）由引擎补全与校验；isMustPlayer/isOnlyOne
            // 等约束为声明字段（UI 层使用），引擎不因目标类型产生错误
            {
                std::unique_ptr<CommandContext> bad(core.createContext(u"/grant @e"));
                ASSERT_NE(bad, nullptr);
                EXPECT_TRUE(bad->getErrorReasons().empty());
                std::unique_ptr<CommandContext> ok(core.createContext(u"/execute myaction @e"));
                ASSERT_NE(ok, nullptr);
                EXPECT_TRUE(ok->getErrorReasons().empty());
                std::unique_ptr<CommandContext> sel(core.createContext(u"/grant @a["));
                ASSERT_NE(sel, nullptr);
                EXPECT_GT(sel->getSuggestions(sel->getCommand().size()).size(), 0u);
                std::unique_ptr<CommandContext> typed(core.createContext(u"/grant @a[type=player]"));
                ASSERT_NE(typed, nullptr);
                EXPECT_TRUE(typed->getErrorReasons().empty());
            }
            // 选择器数据化 V1：自定义变量 @x + 自定义布尔参数 myflag（内置变量同样可用）
            {
                std::unique_ptr<CommandContext> x(core.createContext(u"/grant @x[myflag=true]"));
                ASSERT_NE(x, nullptr);
                EXPECT_TRUE(x->getErrorReasons().empty());
                std::unique_ptr<CommandContext> xf(core.createContext(u"/grant @x[myflag=false]"));
                ASSERT_NE(xf, nullptr);
                EXPECT_TRUE(xf->getErrorReasons().empty());
                std::unique_ptr<CommandContext> xb(core.createContext(u"/grant @x[myflag=1]"));
                ASSERT_NE(xb, nullptr);
                EXPECT_FALSE(xb->getErrorReasons().empty());
                // 内置变量共用扩展参数表
                std::unique_ptr<CommandContext> a(core.createContext(u"/grant @a[myflag=true]"));
                ASSERT_NE(a, nullptr);
                EXPECT_TRUE(a->getErrorReasons().empty());
                // 编辑器候选层：输入 /grant @ 时变量候选应含 @x
                std::unique_ptr<CommandContext> selv(core.createContext(u"/grant @"));
                ASSERT_NE(selv, nullptr);
                EXPECT_TRUE(hasSuggestion(*selv, u"@x"));
                // 编辑器候选层：@a[ 内参数名候选应含 myflag（自定义参数并入全局参数表）
                std::unique_ptr<CommandContext> self(core.createContext(u"/grant @a["));
                ASSERT_NE(self, nullptr);
                EXPECT_TRUE(hasSuggestion(*self, u"myflag"));
                // 自定义变量/@x 与自定义参数名/myflag 的候选带来源徽标
                {
                    bool xFound = false;
                    for (const auto &s: selv->getSuggestions(selv->getCommand().size())) {
                        if (s.content->name == u"@x") {
                            xFound = true;
                            EXPECT_EQ(s.content->packName.value_or(u""), u"测试资源包 A");
                        }
                    }
                    EXPECT_TRUE(xFound);
                    bool keyFound = false;
                    for (const auto &s: self->getSuggestions(self->getCommand().size())) {
                        if (s.content->name == u"myflag") {
                            keyFound = true;
                            EXPECT_EQ(s.content->packName.value_or(u""), u"测试资源包 A");
                        }
                    }
                    EXPECT_TRUE(keyFound);
                }
                // 命令补全的布尔值带中文说明（true=开（是），false=关（否））
                {
                    std::unique_ptr<CommandContext> v(core.createContext(u"/grant @a[myflag="));
                    ASSERT_NE(v, nullptr);
                    bool trueDesc = false;
                    bool falseDesc = false;
                    for (const auto &s: v->getSuggestions(v->getCommand().size())) {
                        if (s.content->name == u"true") {
                            trueDesc = s.content->description == u"开（是）";
                        } else if (s.content->name == u"false") {
                            falseDesc = s.content->description == u"关（否）";
                        }
                    }
                    EXPECT_TRUE(trueDesc);
                    EXPECT_TRUE(falseDesc);
                }
            }
            // 一级补全来源（命令名候选带包名）：/we 的 welcome 候选来源 = 测试资源包 A
            {
                std::unique_ptr<CommandContext> e(core.createContext(u"/we"));
                ASSERT_NE(e, nullptr);
                const size_t index = e->getCommand().size();
                bool found = false;
                for (const auto &s: e->getSuggestions(index)) {
                    if (s.content->name == u"welcome") {
                        found = true;
                        EXPECT_EQ(s.content->packName.value_or(u""), u"测试资源包 A");
                    }
                }
                EXPECT_TRUE(found);
            }
        }
        // A+B 同时启用：A 靠上（先装载）→ welcome 用 A 版（all 在、max 不在、hi 可用）
        {
            Extension::ExtensionPackData a = packFromDir(packA);
            Extension::ExtensionPackData b = packFromDir(packB);
            auto result = Extension::compose(betaVanilla(), {std::move(a), std::move(b)});
            ASSERT_NE(result.cpack, nullptr);
            auto core = CHelperCore(result.cpack);
            std::unique_ptr<CommandContext> ctx(core.createContext(u"/welcome "));
            ASSERT_NE(ctx, nullptr);
            EXPECT_TRUE(hasSuggestion(*ctx, u"all"));
            EXPECT_FALSE(hasSuggestion(*ctx, u"max"));
            std::unique_ptr<CommandContext> hi(core.createContext(u"/hi admin"));
            ASSERT_NE(hi, nullptr);
            EXPECT_TRUE(hi->getErrorReasons().empty());
            std::unique_ptr<CommandContext> max(core.createContext(u"/welcome max"));
            ASSERT_NE(max, nullptr);
            EXPECT_FALSE(max->getErrorReasons().empty());
        }
    }

    TEST(ComposerTest, SelectorDataVariableAndArgumentRules) {
        // 内存构造单个 selector 文件的拓展包夹具
        auto selPack = [](const std::string &body) {
            Extension::ExtensionPackData pack;
            auto bytes = std::make_shared<std::vector<uint8_t>>(body.begin(), body.end());
            pack.files.push_back({"selector/demo.json", std::move(bytes)});
            return pack;
        };
        // 与内置保留变量同名 → 整包拒绝
        {
            auto pack = selPack(R"({"id":"s","variables":[{"name":"@a","description":"x"}]})");
            EXPECT_THROW(Extension::compose(betaVanilla(), {std::move(pack)}), std::exception);
        }
        // 与内置参数同名 → 先到先得忽略并告警
        {
            auto pack = selPack(R"({"id":"s","arguments":[{"name":"type","description":"t","operator":"=","valueType":"BOOLEAN"}]})");
            auto result = Extension::compose(betaVanilla(), {std::move(pack)});
            ASSERT_NE(result.cpack, nullptr);
            EXPECT_NE(std::ranges::find_if(result.warnings, [](const std::string &w) {
                return w.find("selector 参数") != std::string::npos;
            }), result.warnings.end());
        }
        // 合法自定义变量 + 参数（含 !=）可用
        {
            auto pack = selPack(R"({"id":"s","variables":[{"name":"@y","description":"y玩家"}],)"
                                R"("arguments":[{"name":"flag","description":"f","operator":"=!","valueType":"BOOLEAN"}]})");
            auto result = Extension::compose(betaVanilla(), {std::move(pack)});
            ASSERT_NE(result.cpack, nullptr);
            auto core = CHelperCore(result.cpack);
            std::unique_ptr<CommandContext> ctx(core.createContext(u"/give @y[flag=!true] stone"));
            ASSERT_NE(ctx, nullptr);
            EXPECT_TRUE(ctx->getErrorReasons().empty());
        }
    }

}// namespace CHelper::Test
