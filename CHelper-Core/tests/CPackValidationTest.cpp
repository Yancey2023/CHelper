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

#include "CpackTestHelper.h"
#include <chelper/parser/Parser.h>
#include <future>
#include <gtest/gtest.h>

namespace CHelper::Test {

    //非法CPack数据必须在加载/初始化阶段fail-fast，不能进入Parser导致崩溃或未定义行为

    TEST(CPackValidationTest, ValidMinimalCpack) {
        std::unique_ptr<CPack> cpack;
        EXPECT_TRUE(tryCreateCpack(makeCpackJson("[]", "[]", R"([
            {"name": ["list"], "description": "list command", "syntax": ["/list"], "node": {}}
          ])"),
                                   cpack));
        ASSERT_NE(cpack, nullptr);
        //合法的CPack必须能正常解析命令
        CommandContext context(std::shared_ptr<const CPack>(std::move(cpack)), u"list");
        EXPECT_TRUE(context.getErrorReasons().empty());
    }

    TEST(CPackValidationTest, EmptyCommandName) {
        expectCpackRejected(makeCpackJson("[]", "[]", R"([
            {"name": [], "description": "no name", "syntax": ["/list"], "node": {}}
          ])"));
    }

    TEST(CPackValidationTest, EmptySyntax) {
        //syntax为空会导致命令没有任何start node，解析时会产生空OR节点
        expectCpackRejected(makeCpackJson("[]", "[]", R"([
            {"name": ["list"], "description": "no syntax", "syntax": [], "node": {}}
          ])"));
    }

    TEST(CPackValidationTest, UnknownSyntaxToken) {
        expectCpackRejected(makeCpackJson("[]", "[]", R"([
            {"name": ["list"], "description": "unknown token", "syntax": ["/list <arg: missing>"], "node": {}}
          ])"));
    }

    TEST(CPackValidationTest, UnknownJsonKey) {
        expectCpackRejected(makeCpackJson("[]", "[]", R"([
            {"name": ["cmd"], "description": "json command", "syntax": ["/cmd <v: json>"],
             "node": {"<v: json>": {"type": "JSON", "key": "nonexistent"}}}
          ])"));
    }

    TEST(CPackValidationTest, InvalidStartNode) {
        //start指向不存在的node id，必须在初始化阶段报错，否则Parser会使用data为nullptr的节点
        expectCpackRejected(makeCpackJson(R"([
            {"id": "broken", "start": "NONEXISTENT", "node": [
              {"type": "JSON_NULL", "id": "N", "description": "null"}
            ]}
          ])",
                                          "[]", R"([
            {"name": ["cmd"], "description": "json command", "syntax": ["/cmd <v: json>"],
             "node": {"<v: json>": {"type": "JSON", "key": "broken"}}}
          ])"));
    }

    TEST(CPackValidationTest, EmptyJsonEntryValue) {
        //value为空会产生childNodes为空的OR节点
        expectCpackRejected(makeCpackJson(R"([
            {"id": "broken", "start": "P", "node": [
              {"type": "JSON_OBJECT", "id": "P", "description": "object", "data": [
                {"key": "s", "description": "empty value", "value": []}
              ]}
            ]}
          ])",
                                          "[]", R"([
            {"name": ["cmd"], "description": "json command", "syntax": ["/cmd <v: json>"],
             "node": {"<v: json>": {"type": "JSON", "key": "broken"}}}
          ])"));
    }

    TEST(CPackValidationTest, UnknownJsonEntryValueId) {
        expectCpackRejected(makeCpackJson(R"([
            {"id": "broken", "start": "P", "node": [
              {"type": "JSON_OBJECT", "id": "P", "description": "object", "data": [
                {"key": "s", "description": "unknown value", "value": ["NONEXISTENT"]}
              ]}
            ]}
          ])",
                                          "[]", R"([
            {"name": ["cmd"], "description": "json command", "syntax": ["/cmd <v: json>"],
             "node": {"<v: json>": {"type": "JSON", "key": "broken"}}}
          ])"));
    }

    TEST(CPackValidationTest, UnknownJsonListElementId) {
        expectCpackRejected(makeCpackJson(R"([
            {"id": "broken", "start": "P", "node": [
              {"type": "JSON_LIST", "id": "P", "description": "list", "data": "NONEXISTENT"}
            ]}
          ])",
                                          "[]", R"([
            {"name": ["cmd"], "description": "json command", "syntax": ["/cmd <v: json>"],
             "node": {"<v: json>": {"type": "JSON", "key": "broken"}}}
          ])"));
    }

    TEST(CPackValidationTest, EmptyRepeatNodes) {
        //repeatNodes为空会产生childNodes为空的OR节点
        expectCpackRejected(makeCpackJson("[]", R"([
            {"id": "broken", "breakNodes": [], "isEnd": [], "repeatNodes": []}
          ])",
                                          R"([
            {"name": ["cmd"], "description": "repeat command", "syntax": ["/cmd <r: repeat>"],
             "node": {"<r: repeat>": {"type": "REPEAT", "key": "broken"}}}
          ])"));
    }

    TEST(CPackValidationTest, RepeatNodesSizeMismatch) {
        expectCpackRejected(makeCpackJson("[]", R"([
            {"id": "broken", "breakNodes": [], "isEnd": [true], "repeatNodes": []}
          ])",
                                          R"([
            {"name": ["cmd"], "description": "repeat command", "syntax": ["/cmd <r: repeat>"],
             "node": {"<r: repeat>": {"type": "REPEAT", "key": "broken"}}}
          ])"));
    }

    TEST(CPackValidationTest, UnknownRepeatKey) {
        expectCpackRejected(makeCpackJson("[]", "[]", R"([
            {"name": ["cmd"], "description": "repeat command", "syntax": ["/cmd <r: repeat>"],
             "node": {"<r: repeat>": {"type": "REPEAT", "key": "nonexistent"}}}
          ])"));
    }

    TEST(CPackValidationTest, NodeCreateStageFollowsContext) {
        //加载阶段随反序列化上下文传递：JSON节点在JSON_NODE阶段合法，在其他阶段必须被拒绝。
        //阶段曾经是全局变量，多线程同时创建CPack时会互相覆盖，这里锁住上下文这条路径
        const std::string jsonNodes = R"([{"type": "JSON_NULL", "id": "N", "description": "null"}])";
        {
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::JSON_NODE;
            Node::FreeableNodeWithTypes nodes;
            EXPECT_NO_THROW(readJson(nodes, jsonNodes, ctx));
            ASSERT_EQ(nodes.nodes.size(), size_t{1});
            EXPECT_EQ(nodes.nodes[0].nodeTypeId, Node::NodeTypeId::JSON_NULL);
        }
        {
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::REPEAT_NODE;
            Node::FreeableNodeWithTypes nodes;
            EXPECT_THROW(readJson(nodes, jsonNodes, ctx), std::runtime_error);
        }
        {
            //未携带阶段的普通上下文不限制节点类型
            Node::FreeableNodeWithTypes nodes;
            EXPECT_NO_THROW(readJson(nodes, jsonNodes));
            ASSERT_EQ(nodes.nodes.size(), size_t{1});
            EXPECT_EQ(nodes.nodes[0].nodeTypeId, Node::NodeTypeId::JSON_NULL);
        }
    }

    TEST(CPackValidationTest, GrammarStageAllowsOnlyGrammarNodes) {
        const std::string grammar = R"({
          "type": "AND",
          "id": "AND_NODE",
          "nodes": []
        })";
        {
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::GRAMMAR_NODE;
            Node::NodeWithType node;
            EXPECT_NO_THROW(readJson(node, grammar, ctx));
            EXPECT_EQ(node.nodeTypeId, Node::NodeTypeId::AND);
        }
        {
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::COMMAND_PARAM_NODE;
            Node::NodeWithType node;
            EXPECT_THROW(readJson(node, grammar, ctx), std::runtime_error);
        }
        {
            NodeReadContext ctx;
            ctx.createStage = Node::NodeCreateStage::GRAMMAR_NODE;
            Node::NodeWithType node;
            EXPECT_THROW(readJson(node, R"({"type":"COMMAND"})", ctx), std::runtime_error);
        }
    }

    TEST(CPackValidationTest, GrammarResourceCanAddNodeWithoutCxxChanges) {
        // 只增加资源节点和 ID 引用，C++ 不需要认识 custom。
        // NORMAL_ID 用内联键表匹配整段token；EQUAL_ENTRY 用键表+值节点表达键值对
        const std::string grammar = R"([
          {"id":"dynamic","type":"grammar","content":{
            "id":"dynamic","node":[
              {"type":"NORMAL_ID","id":"CUSTOM_KEY","contents":[
                {"name":"custom","description":"自定义键"}
              ]},
              {"type":"SINGLE_SYMBOL","id":"CUSTOM_EQUAL","symbol":"=","isAddSpace":false},
              {"type":"INTEGER","id":"CUSTOM_INTEGER"},
              {"type":"EQUAL_ENTRY","id":"CUSTOM_ROOT","values":[
                {"name":"custom","description":"自定义键","canUseNotEqual":false,"value":"CUSTOM_INTEGER"}
              ]}
            ],"start":"CUSTOM_ROOT"}}
        ])";
        auto cpackJson = makeCpackJson("[]", "[]", R"([
          {"name":["list"],"description":"list","syntax":["/list"],"node":{}}
        ])");
        const auto grammarPosition = cpackJson.rfind("\n}");
        ASSERT_NE(grammarPosition, std::string::npos);
        cpackJson.insert(grammarPosition, ",\n  \"grammar\": " + grammar);
        std::unique_ptr<CPack> cpack;
        ASSERT_TRUE(tryCreateCpack(cpackJson, cpack));
        ASSERT_NE(cpack, nullptr);
        const auto *root = cpack->getGrammar("dynamic");
        ASSERT_NE(root, nullptr);
        EXPECT_TRUE(Parser::parse(u"custom=123", *root).errorReasons.empty());
    }

    TEST(CPackValidationTest, EmptyEqualEntryValues) {
        //equalDatas为空会导致键表为空，任何键都无法匹配，必须在加载阶段拒绝
        std::string cpackJson = makeCpackJson("[]", "[]", R"([
          {"name":["list"],"description":"list","syntax":["/list"],"node":{}}
        ])");
        const auto grammarPosition = cpackJson.rfind("\n}");
        ASSERT_NE(grammarPosition, std::string::npos);
        cpackJson.insert(grammarPosition, R"(,
  "grammar": [
    {"id":"broken","type":"grammar","content":{
      "id":"broken","node":[
        {"type":"INTEGER","id":"CUSTOM_INTEGER"}
      ],"start":"CUSTOM_ROOT"}}
  ])");
        expectCpackRejected(cpackJson);
    }

    TEST(CPackValidationTest, GrammarGraphBinaryRoundTrip) {
        const std::filesystem::path resourceDir(RESOURCE_DIR);
        auto source = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "experiment");
        ASSERT_NE(source, nullptr);
        const auto binaryPath = std::filesystem::temp_directory_path() / "chelper-grammar-graph-test.cpack";
        std::error_code error;
        std::filesystem::remove(binaryPath, error);
        ASSERT_NO_THROW(source->writeBinToFile(binaryPath));

        std::ifstream stream(binaryPath, std::ios::binary);
        ASSERT_TRUE(stream.is_open());
        const std::string binary((std::istreambuf_iterator<char>(stream)), std::istreambuf_iterator<char>());
        auto restored = CHelper::serialization::createCPackByBinary(binary);
        ASSERT_NE(restored, nullptr);
        const auto *root = restored->getGrammar("target_selector");
        ASSERT_NE(root, nullptr);
        EXPECT_TRUE(Parser::parse(u"@e[type=minecraft:zombie]", *root).errorReasons.empty());
        std::filesystem::remove(binaryPath, error);
    }

    TEST(CPackValidationTest, PeekNodeTypeName) {
        //写入端把 "type" 固定放在第一个成员，节点读取靠预读第一个成员直接取到类型名，
        //因此预读必须命中该布局；顺序不同时返回 false，由完整扫描兜底
        constexpr auto opts = glz::opts{.error_on_unknown_keys = false};
        NodeReadContext ctx;
        {
            std::string json = R"({"type": "JSON_NULL", "id": "N"})";
            std::string_view typeName;
            auto it = json.data();
            const auto end = json.data() + json.size();
            EXPECT_TRUE((peekNodeTypeName<glz::JSON, opts>(typeName, ctx, it, end)));
            EXPECT_EQ(typeName, "JSON_NULL");
        }
        {
            std::string json = R"({"id": "N", "type": "JSON_NULL"})";
            std::string_view typeName;
            auto it = json.data();
            const auto end = json.data() + json.size();
            EXPECT_FALSE((peekNodeTypeName<glz::JSON, opts>(typeName, ctx, it, end)));
        }
        {
            std::string msgpack;
            auto obj = glz::obj{"type", "JSON_NULL", "id", "N"};
            EXPECT_FALSE(bool(glz::write_msgpack(obj, msgpack)));
            std::string_view typeName;
            auto it = msgpack.data();
            const auto end = msgpack.data() + msgpack.size();
            EXPECT_TRUE((peekNodeTypeName<glz::MSGPACK, opts>(typeName, ctx, it, end)));
            EXPECT_EQ(typeName, "JSON_NULL");
        }
    }

    TEST(CPackValidationTest, NodeTypeNotFirst) {
        //"type" 不在第一个成员时回退到完整扫描，节点仍然要能被正确读取
        std::unique_ptr<CPack> cpack;
        EXPECT_TRUE(tryCreateCpack(makeCpackJson(R"([
            {"id": "json1", "start": "N", "node": [
              {"id": "N", "description": "null", "type": "JSON_NULL"}
            ]}
          ])"),
                                   cpack));
        ASSERT_NE(cpack, nullptr);
        EXPECT_EQ(cpack->jsonNodes.size(), size_t{1});
    }

    TEST(CPackValidationTest, ConcurrentCpackCreation) {
        //多个线程同时创建CPack：任何一个线程的加载阶段都不能影响其他线程，
        //否则后完成的线程会把自己的阶段覆盖到正在读取节点的线程上，导致加载随机失败
        const std::string json = makeCpackJson(R"([
            {"id": "json1", "start": "N", "node": [
              {"type": "JSON_NULL", "id": "N", "description": "null"}
            ]}
          ])",
                                               R"([
            {"id": "repeat1",
             "breakNodes": [{"type": "STRING", "id": "B", "description": "break"}],
             "isEnd": [true],
             "repeatNodes": [[{"type": "STRING", "id": "S", "description": "string"}]]}
          ])",
                                               R"([
            {"name": ["list"], "description": "list command", "syntax": ["/list"], "node": {}}
          ])");
        constexpr int32_t roundCount = 4;
        constexpr int32_t threadCount = 8;
        for (int32_t round = 0; round < roundCount; ++round) {
            std::vector<std::future<bool>> futures;
            futures.reserve(threadCount);
            for (int32_t i = 0; i < threadCount; ++i) {
                futures.emplace_back(std::async(std::launch::async, [&json]() {
                    std::unique_ptr<CPack> cpack;
                    return tryCreateCpack(json, cpack);
                }));
            }
            for (auto &future: futures) {
                EXPECT_TRUE(future.get()) << "round " << round;
            }
        }
    }

}// namespace CHelper::Test
