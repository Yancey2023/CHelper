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

#include <chelper/node/CommandNode.h>
#include <chelper/node/NodeInitialization.h>
#include <chelper/resources/CPack.h>
#include <chelper/serialization/BinaryFormat.h>
#include <chelper/serialization/IO.h>
#include <chelper/serialization/SerializationImpl.h>
#include <gtest/gtest.h>

namespace std {

    template<class T>
    bool operator==(const std::shared_ptr<T> &t1, const std::shared_ptr<T> &t2) {// NOLINT(*-dcl58-cpp)
        return *t1 == *t2;
    }

}// namespace std

namespace CHelper {

    bool operator==(const Manifest &t1, const Manifest &t2) {
        return t1.name == t2.name &&
               t1.description == t2.description &&
               t1.version == t2.version &&
               t1.versionType == t2.versionType &&
               t1.branch == t2.branch &&
               t1.author == t2.author &&
               t1.updateDate == t2.updateDate &&
               t1.packId == t2.packId &&
               t1.versionCode == t2.versionCode &&
               t1.isBasicPack == t2.isBasicPack &&
               t1.isDefault == t2.isDefault;
    }

    bool operator==(const NormalId &t1, const NormalId &t2) {
        return t1.name == t2.name &&
               t1.description == t2.description;
    }

    bool operator==(const NamespaceId &t1, const NamespaceId &t2) {
        if (!(static_cast<const NormalId &>(t1) == static_cast<const NormalId &>(t2))) {
            return false;
        }
        return t1.idNamespace == t2.idNamespace;
    }

    bool operator==(const ItemId &t1, const ItemId &t2) {
        if (!(static_cast<const NamespaceId &>(t1) == static_cast<const NamespaceId &>(t2))) {
            return false;
        }
        return t1.max == t2.max && t1.descriptions == t2.descriptions;
    }

    bool operator==(const Property &t1, const Property &t2) {
        if (t1.type != t2.type || t1.name != t2.name) {
            return false;
        }
        switch (t1.type) {
            case PropertyType::BOOLEAN:
                if (t1.defaultValue.boolean != t2.defaultValue.boolean) {
                    return false;
                }
                break;
            case PropertyType::STRING:
                if (*t1.defaultValue.string != *t2.defaultValue.string) {
                    return false;
                }
                break;
            case PropertyType::INTEGER:
                if (t1.defaultValue.integer != t2.defaultValue.integer) {
                    return false;
                }
                break;
            default:
                CHELPER_UNREACHABLE();
        }
        if (t1.valid.has_value() != t2.valid.has_value()) {
            return false;
        }
        if (t1.valid.has_value() && t2.valid.has_value()) {
            if (t1.valid.value().size() != t2.valid.value().size()) {
                return false;
            }
            for (size_t i = 0; i < t1.valid.value().size(); ++i) {
                switch (t1.type) {
                    case PropertyType::BOOLEAN:
                        if (t1.valid.value()[i].boolean != t2.valid.value()[i].boolean) {
                            return false;
                        }
                        break;
                    case PropertyType::STRING:
                        if (*t1.valid.value()[i].string != *t2.valid.value()[i].string) {
                            return false;
                        }
                        break;
                    case PropertyType::INTEGER:
                        if (t1.valid.value()[i].integer != t2.valid.value()[i].integer) {
                            return false;
                        }
                        break;
                    default:
                        CHELPER_UNREACHABLE();
                }
            }
        }
        return true;
    }

    bool operator==(const BlockId &t1, const BlockId &t2) {
        if (!(static_cast<const NamespaceId &>(t1) == static_cast<const NamespaceId &>(t2))) {
            return false;
        }
        return t1.properties == t2.properties;
    }

    bool operator==(const BlockPropertyDescription &t1, const BlockPropertyDescription &t2) {
        if (t1.type != t2.type || t1.propertyName != t2.propertyName || t1.description != t2.description) {
            return false;
        }
        if (t1.values.size() != t2.values.size()) {
            return false;
        }
        for (size_t i = 0; i < t1.values.size(); ++i) {
            switch (t1.type) {
                case PropertyType::BOOLEAN:
                    if (t1.values[i].valueName.boolean != t2.values[i].valueName.boolean) {
                        return false;
                    }
                    break;
                case PropertyType::STRING:
                    if (*t1.values[i].valueName.string != *t2.values[i].valueName.string) {
                        return false;
                    }
                    break;
                case PropertyType::INTEGER:
                    if (t1.values[i].valueName.integer != t2.values[i].valueName.integer) {
                        return false;
                    }
                    break;
                default:
                    CHELPER_UNREACHABLE();
            }
            if (t1.values[i].description != t2.values[i].description) {
                return false;
            }
        }
        return true;
    }

    bool operator==(const PerBlockPropertyDescription &t1, const PerBlockPropertyDescription &t2) {
        return t1.blocks == t2.blocks && t1.properties == t2.properties;
    }

    bool operator==(const BlockPropertyDescriptions &t1, const BlockPropertyDescriptions &t2) {
        return t1.common == t2.common && t1.block == t2.block;
    }

    bool operator==(const BlockIds &t1, const BlockIds &t2) {
        return t1.blockStateValues == t2.blockStateValues && t1.blockPropertyDescriptions == t2.blockPropertyDescriptions;
    }

    namespace Node {

        bool operator==(const NodeSerializable &t1, const NodeSerializable &t2) {
            return t1.id == t2.id &&
                   t1.brief == t2.brief &&
                   t1.description == t2.description &&
                   t1.isMustAfterSpace == t2.isMustAfterSpace;
        }

        bool operator==(const NodeJsonBoolean &t1, const NodeJsonBoolean &t2) {
            if (!(static_cast<const NodeSerializable &>(t1) == static_cast<const NodeSerializable &>(t2))) {
                return false;
            }
            return t1.descriptionTrue == t2.descriptionTrue &&
                   t1.descriptionFalse == t2.descriptionFalse;
        }

        bool operator==(const NodeJsonNull &t1, const NodeJsonNull &t2) {
            if (!(static_cast<const NodeSerializable &>(t1) == static_cast<const NodeSerializable &>(t2))) {
                return false;
            }
            return true;
        }

        template<class T, bool isJson>
        bool operator==(const NodeTemplateNumber<T, isJson> &t1, const NodeTemplateNumber<T, isJson> &t2) {
            if (!(static_cast<const NodeSerializable &>(t1) == static_cast<const NodeSerializable &>(t2))) {
                return false;
            }
            return t1.min == t2.min && t1.max == t2.max;
        }

    }// namespace Node

}// namespace CHelper

// 多格式 roundtrip：自定义二进制 + glaze 自带格式
template<auto Write, auto Read>
void roundtrip(const auto &t1) {
    using T = std::decay_t<decltype(t1)>;
    std::string buffer;
    EXPECT_FALSE(bool(Write(t1, buffer)));
    T t2;
    const auto rec = Read(t2, buffer);
    if (bool(rec)) {
        std::ostringstream hex;
        for (const unsigned char c: buffer) {
            char temp[4];
            snprintf(temp, sizeof(temp), "%02x ", c);
            hex << temp;
        }
        ADD_FAILURE() << "read error: " << glz::format_error(rec, buffer) << "\nbuffer: " << hex.str();
    }
    EXPECT_EQ(t1, t2);
}

template<class T>
void test(const std::function<T()> &getInstance) {
    T t1 = getInstance();
    {
        // 自定义二进制格式
        std::string buffer;
        EXPECT_FALSE(bool(glz::write<glz::opts{.format = CHelper::BinaryFormat}>(t1, buffer)));
        T t2;
        const auto rec = glz::read<glz::opts{.format = CHelper::BinaryFormat}>(t2, buffer);
        if (bool(rec)) {
            ADD_FAILURE() << "binary read error: " << glz::format_error(rec, buffer);
        }
        EXPECT_EQ(t1, t2);
    }
    {
        // JSON
        std::string buffer;
        EXPECT_NO_THROW(buffer = CHelper::writeJson(t1));
        T t2;
        EXPECT_NO_THROW(CHelper::readJson(t2, buffer));
        EXPECT_EQ(t1, t2);
    }
    if constexpr (glz::write_supported<T, glz::MSGPACK> && glz::read_supported<T, glz::MSGPACK>) {
        // MessagePack
        std::string buffer;
        EXPECT_FALSE(bool(glz::write_msgpack(t1, buffer)));
        T t2;
        const auto rec = glz::read_msgpack(t2, buffer);
        if (bool(rec)) {
            std::ostringstream hex;
            for (const unsigned char c: buffer) {
                char temp[4];
                snprintf(temp, sizeof(temp), "%02x ", c);
                hex << temp;
            }
            ADD_FAILURE() << "msgpack read error: " << glz::format_error(rec, buffer) << "\nbuffer: " << hex.str();
        }
        EXPECT_EQ(t1, t2);
    }
}

template<class T>
void testNode(CHelper::CPack &cpack, const std::function<T()> &getInstance) {
    T t1 = getInstance();
    {
        // 自定义二进制格式
        std::string buffer;
        EXPECT_FALSE(bool(glz::write<glz::opts{.format = CHelper::BinaryFormat}>(t1, buffer)));
        T t2;
        const auto rec = glz::read<glz::opts{.format = CHelper::BinaryFormat}>(t2, buffer);
        if (bool(rec)) {
            ADD_FAILURE() << "binary read error: " << glz::format_error(rec, buffer);
        }
        CHelper::Node::initNode(t2, cpack);
        EXPECT_EQ(t1, t2);
    }
    {
        // JSON
        std::string buffer;
        EXPECT_NO_THROW(buffer = CHelper::writeJson(t1));
        T t2;
        EXPECT_NO_THROW(CHelper::readJson(t2, buffer));
        CHelper::Node::initNode(t2, cpack);
        EXPECT_EQ(t1, t2);
    }
    {
        // MessagePack
        std::string buffer;
        EXPECT_FALSE(bool(glz::write_msgpack(t1, buffer)));
        T t2;
        const auto rec = glz::read_msgpack(t2, buffer);
        if (bool(rec)) {
            ADD_FAILURE() << "msgpack read error: " << glz::format_error(rec, buffer);
        }
        CHelper::Node::initNode(t2, cpack);
        EXPECT_EQ(t1, t2);
    }
}

// Property 等手写编解码类型仅支持 JSON / MessagePack / 自定义二进制格式
template<class T>
void testHandwritten(const std::function<T()> &getInstance) {
    T t1 = getInstance();
    {
        std::string buffer;
        EXPECT_FALSE(bool(glz::write<glz::opts{.format = CHelper::BinaryFormat}>(t1, buffer)));
        T t2;
        const auto rec = glz::read<glz::opts{.format = CHelper::BinaryFormat}>(t2, buffer);
        if (bool(rec)) {
            ADD_FAILURE() << "binary read error: " << glz::format_error(rec, buffer);
        }
        EXPECT_EQ(t1, t2);
    }
    {
        std::string buffer;
        EXPECT_NO_THROW(buffer = CHelper::writeJson(t1));
        T t2;
        EXPECT_NO_THROW(CHelper::readJson(t2, buffer));
        EXPECT_EQ(t1, t2);
    }
    {
        std::string buffer;
        EXPECT_FALSE(bool(glz::write_msgpack(t1, buffer)));
        T t2;
        EXPECT_FALSE(bool(glz::read_msgpack(t2, buffer)));
        EXPECT_EQ(t1, t2);
    }
}

template<class T>
void test(const std::vector<std::function<T()>> &getInstance) {
    for (const auto &item: getInstance) {
        test(item);
    }
}

template<class T>
void testHandwritten(const std::vector<std::function<T()>> &getInstance) {
    for (const auto &item: getInstance) {
        testHandwritten(item);
    }
}

template<class T>
void testNode(CHelper::CPack &cpack,
              const std::vector<std::function<T()>> &getInstance) {
    for (const auto &item: getInstance) {
        testNode(cpack, item);
    }
}

TEST(BinaryUtilTest, String) {
    auto getInstance1 = []() { return u""; };
    auto getInstance2 = []() { return u"Yancey"; };
    test<std::u16string>({getInstance1, getInstance2});
}

TEST(BinaryUtilTest, OptioanlString) {
    auto getInstance1 = []() { return u""; };
    auto getInstance2 = []() { return u"Yancey"; };
    auto getInstance3 = []() { return std::nullopt; };
    test<std::optional<std::u16string>>({getInstance1, getInstance2, getInstance3});
}

TEST(BinaryUtilTest, Manifest) {
    auto getInstance1 = []() -> CHelper::Manifest {
        return {u"name", u"description", u"version", u"versionType",
                u"branch", u"author", u"updateDate", u"packId",
                1, true, true};
    };
    auto getInstance2 = []() -> CHelper::Manifest {
        return {u"name", std::nullopt, u"version", u"versionType",
                u"branch", u"author", u"updateDate", u"packId",
                2, std::nullopt, true};
    };
    auto getInstance3 = []() -> CHelper::Manifest {
        return {u"name", u"description", std::nullopt, u"versionType",
                u"branch", u"author", u"updateDate", u"packId",
                3, false, std::nullopt};
    };
    auto getInstance4 = []() -> CHelper::Manifest {
        return {std::nullopt,
                std::nullopt,
                std::nullopt,
                std::nullopt,
                std::nullopt,
                u"author",
                u"updateDate",
                u"packId",
                5,
                std::nullopt,
                std::nullopt};
    };
    test<CHelper::Manifest>({getInstance1, getInstance2, getInstance3, getInstance4});
}

TEST(BinaryUtilTest, NormalId) {
    auto getInstance1 = []() {
        return CHelper::NormalId::make(u"name", u"description");
    };
    auto getInstance2 = []() {
        return CHelper::NormalId::make(u"name", u"");
    };
    auto getInstance3 = []() {
        return CHelper::NormalId::make(u"name", std::nullopt);
    };
    test<std::shared_ptr<CHelper::NormalId>>({getInstance1, getInstance2, getInstance3});
}

TEST(BinaryUtilTest, NamespaceId) {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    CHelper::IdEntry entry;
    CHelper::readJsonFromFile(entry, resourceDir / "resources" / "beta" / "vanilla" / "id" / "entity.json");
    auto &namespaceEntry = std::get<CHelper::NamespaceIdEntry>(entry);
    std::vector<std::function<CHelper::NamespaceId()>> getInstance;
    for (const auto &item: *namespaceEntry.content) {
        getInstance.emplace_back([item]() { return *item; });
    }
    test<CHelper::NamespaceId>(getInstance);
}

TEST(BinaryUtilTest, ItemId) {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    CHelper::IdEntry entry;
    CHelper::readJsonFromFile(entry, resourceDir / "resources" / "beta" / "vanilla" / "id" / "item.json");
    auto &itemEntry = std::get<CHelper::ItemIdsEntry>(entry);
    std::vector<std::function<std::shared_ptr<CHelper::ItemId>()>> getInstance;
    for (const auto &item: *itemEntry.content) {
        getInstance.emplace_back([item]() { return item; });
    }
    test<std::shared_ptr<CHelper::ItemId>>(getInstance);
}

TEST(BinaryUtilTest, Property) {
    auto getInstance1 = []() {
        CHelper::Property aProperty;
        aProperty.name = u"name1";
        aProperty.type = CHelper::PropertyType::BOOLEAN;
        aProperty.defaultValue.boolean = true;
        aProperty.valid = std::pmr::vector<CHelper::PropertyValue>(2);
        aProperty.valid->at(0).boolean = true;
        aProperty.valid->at(1).boolean = false;
        return aProperty;
    };
    auto getInstance2 = []() {
        CHelper::Property aProperty;
        aProperty.name = u"name2";
        aProperty.type = CHelper::PropertyType::INTEGER;
        aProperty.defaultValue.integer = 0;
        aProperty.valid = std::pmr::vector<CHelper::PropertyValue>(3);
        aProperty.valid->at(0).integer = 0;
        aProperty.valid->at(1).integer = 1;
        aProperty.valid->at(2).integer = 2;
        return aProperty;
    };
    auto getInstance3 = []() {
        CHelper::Property aProperty;
        aProperty.name = u"name3";
        aProperty.type = CHelper::PropertyType::STRING;
        aProperty.defaultValue.string = new std::pmr::u16string(u"aaa");
        aProperty.valid = std::pmr::vector<CHelper::PropertyValue>(3);
        aProperty.valid->at(0).string = new std::pmr::u16string(u"a1");
        aProperty.valid->at(1).string = new std::pmr::u16string(u"a2");
        aProperty.valid->at(2).string = new std::pmr::u16string(u"a3");
        return aProperty;
    };
    testHandwritten<CHelper::Property>({getInstance1, getInstance2, getInstance3});
}

TEST(BinaryUtilTest, NodeJsonElementBinary) {
    // 旧版二进制格式：id 必有值，直接写字符串（无 optional 标记）
    CHelper::Node::NodeJsonElement element;
    element.id = "components";
    element.startNodeId = "PARENT";
    std::string buffer;
    ASSERT_FALSE(bool(glz::write<glz::opts{.format = CHelper::BinaryFormat}>(element, buffer)));
    std::u16string idBack = u"mismatch";
    std::uint32_t len = 0;
    // 手动按旧格式解码校验：uint32 长度 + UTF-8 字节
    len = (static_cast<std::uint32_t>(buffer[0]) | (static_cast<std::uint32_t>(buffer[1]) << 8) |
           (static_cast<std::uint32_t>(buffer[2]) << 16) | (static_cast<std::uint32_t>(buffer[3]) << 24));
    EXPECT_EQ(len, 10);
    idBack = utf8::utf8to16(std::string(buffer.data() + 4, len));
    EXPECT_EQ(idBack, u"components");
}

TEST(BinaryUtilTest, NestedMapBinary) {
    using BlockFixData = std::unordered_map<
            std::u16string,
            std::unordered_map<std::uint32_t, std::pair<std::optional<std::u16string>, std::optional<std::u16string>>>>;
    using Inner = BlockFixData::mapped_type;
    // 记录该布局所依赖的概念约束（readable_map_t 的构成与 pair 不属于 reflectable 的事实）
    static_assert(glz::range<Inner>);
    static_assert(glz::pair_t<glz::range_value_t<Inner>>);
    static_assert(!glz::custom_read<Inner>);
    static_assert(!glz::meta_value_t<Inner>);
    static_assert(!glz::str_t<Inner>);
    static_assert(glz::readable_map_t<Inner>);
    static_assert(glz::range<BlockFixData>);
    static_assert(glz::pair_t<glz::range_value_t<BlockFixData>>);
    static_assert(!glz::custom_read<BlockFixData>);
    static_assert(!glz::meta_value_t<BlockFixData>);
    static_assert(!glz::str_t<BlockFixData>);
    static_assert(glz::readable_map_t<BlockFixData>);
    static_assert(glz::writable_map_t<BlockFixData>);
    static_assert(glz::readable_map_t<BlockFixData::mapped_type>);
    static_assert(glz::writable_map_t<BlockFixData::mapped_type>);
    BlockFixData data;
    auto &inner = data[u"stone"];
    inner[0] = {u"stone", std::nullopt};
    inner[1] = {std::nullopt, u"smooth"};
    data[u"dirt"] = {};

    std::string buffer;
    ASSERT_FALSE(bool(glz::write<glz::opts{.format = CHelper::BinaryFormat}>(data, buffer)));
    BlockFixData back;
    const auto rec = glz::read<glz::opts{.format = CHelper::BinaryFormat}>(back, buffer);
    if (bool(rec)) {
        ADD_FAILURE() << "binary read error: " << glz::format_error(rec, buffer);
    }
    EXPECT_EQ(back.size(), 2);
    EXPECT_EQ(back[u"stone"].size(), 2);
    EXPECT_EQ(back[u"stone"][0].first.value(), u"stone");
    EXPECT_EQ(back[u"stone"][1].second.value(), u"smooth");
    EXPECT_TRUE(back[u"dirt"].empty());
}

TEST(BinaryUtilTest, BlockId) {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    CHelper::IdEntry entry;
    CHelper::readJsonFromFile(entry, resourceDir / "resources" / "beta" / "vanilla" / "id" / "block.json");
    auto &blockEntry = std::get<CHelper::BlockIdsEntry>(entry);
    testHandwritten<CHelper::BlockIds>([&blockEntry]() { return *blockEntry.content; });
}

TEST(BinaryUtilTest, PerCPackNormalIds) {

    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::serialization::createCPackByDirectory(
                std::filesystem::path(resourceDir / "resources" / "beta" / "vanilla"));
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    std::vector<std::function<
            std::shared_ptr<std::pmr::vector<std::shared_ptr<CHelper::NormalId>>>()>>
            getInstance;
    for (const auto &item: cpack->normalIds) {
        const auto &ids = item.second;
        getInstance.emplace_back([&ids]() { return ids; });
    }
    test<std::shared_ptr<std::pmr::vector<std::shared_ptr<CHelper::NormalId>>>>(getInstance);
}

TEST(BinaryUtilTest, CPackNormalIds) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" /
                                                               "beta" / "vanilla");
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    test<decltype(cpack->normalIds)>(
            [&cpack]() { return cpack->normalIds; });
}

TEST(BinaryUtilTest, CPackNamespaceId) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" /
                                                               "beta" / "vanilla");
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    test<decltype(cpack->namespaceIds)>(
            [&cpack]() { return cpack->namespaceIds; });
}

TEST(BinaryUtilTest, NodeJsonBoolean) {
    std::unique_ptr<CHelper::CPack> cpack;
    std::filesystem::path resourceDir(RESOURCE_DIR);
    try {
        cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "vanilla");
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    CHelper::Node::NodeJsonBoolean node("ID", u"description", u"descriptionTrue", u"descriptionFalse");
    testNode<CHelper::Node::NodeJsonBoolean>(*cpack, [&node]() { return node; });
}

TEST(BinaryUtilTest, NodeJsonInteger) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" /
                                                               "beta" / "vanilla");
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    testNode<CHelper::Node::NodeJsonInteger>(
            *cpack,
            {
                    []() {
                        CHelper::Node::NodeJsonInteger node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = 0;
                        node.max = 3;
                        return node;
                    },
                    []() {
                        CHelper::Node::NodeJsonInteger node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = std::nullopt;
                        node.max = 3;
                        return node;
                    },
                    []() {
                        CHelper::Node::NodeJsonInteger node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = 1;
                        node.max = std::nullopt;
                        return node;
                    },
                    []() {
                        CHelper::Node::NodeJsonInteger node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = std::nullopt;
                        node.max = std::nullopt;
                        return node;
                    },
            });
}

TEST(BinaryUtilTest, NodeJsonFloat) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" /
                                                               "beta" / "vanilla");
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    testNode<CHelper::Node::NodeJsonFloat>(
            *cpack,
            {
                    []() {
                        CHelper::Node::NodeJsonFloat node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = 0.0F;
                        node.max = 3.0F;
                        return node;
                    },
                    []() {
                        CHelper::Node::NodeJsonFloat node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = std::nullopt;
                        node.max = 3.0F;
                        return node;
                    },
                    []() {
                        CHelper::Node::NodeJsonFloat node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = 1.0F;
                        node.max = std::nullopt;
                        return node;
                    },
                    []() {
                        CHelper::Node::NodeJsonFloat node;
                        node.id = "ID";
                        node.description = u"description";
                        node.isMustAfterSpace = false;
                        node.min = std::nullopt;
                        node.max = std::nullopt;
                        return node;
                    },
            });
}

TEST(BinaryUtilTest, NodeJsonNull) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "vanilla");
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    testNode<CHelper::Node::NodeJsonNull>(
            *cpack, []() { return CHelper::Node::NodeJsonNull{"ID", u"description"}; });
}

// NodePerCommand 的 MessagePack 表示（含预解析的节点图）必须能完整往返
TEST(BinaryUtilTest, NodePerCommandMsgpack) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "vanilla");
    } catch (const std::exception &e) {
        SPDLOG_ERROR("{}", e.what());
        exit(-1);
    }
    const auto getDefinitionIndex = [](const CHelper::Node::NodePerCommand &command, const CHelper::Node::NodeWithType &node) {
        for (size_t i = 0; i < command.nodes.nodes.size(); ++i) {
            if (command.nodes.nodes[i].data == node.data) {
                return i;
            }
        }
        return SIZE_MAX;
    };
    for (const auto &command: *cpack->commands) {
        std::string buffer;
        EXPECT_FALSE(bool(glz::write_msgpack(command, buffer)));
        CHelper::Node::NodePerCommand command2;
        const auto rec = glz::read_msgpack(command2, buffer);
        if (bool(rec)) {
            ADD_FAILURE() << "msgpack read error: " << glz::format_error(rec, buffer);
            continue;
        }
        EXPECT_EQ(command2.name, command.name);
        EXPECT_EQ(command2.description, command.description);
        EXPECT_EQ(command2.syntax, command.syntax);
        ASSERT_EQ(command2.nodes.nodes.size(), command.nodes.nodes.size());
        for (size_t i = 0; i < command.nodes.nodes.size(); ++i) {
            EXPECT_EQ(command2.nodes.nodes[i].nodeTypeId, command.nodes.nodes[i].nodeTypeId);
            EXPECT_EQ(static_cast<const CHelper::Node::NodeSerializable *>(command2.nodes.nodes[i].data)->id,
                      static_cast<const CHelper::Node::NodeSerializable *>(command.nodes.nodes[i].data)->id);
        }
        ASSERT_EQ(command2.wrappedNodes.size(), command.wrappedNodes.size());
        for (size_t i = 0; i < command.wrappedNodes.size(); ++i) {
            const size_t definitionIndex = getDefinitionIndex(command, command.wrappedNodes[i].innerNode);
            ASSERT_NE(definitionIndex, SIZE_MAX);
            EXPECT_EQ(command2.wrappedNodes[i].innerNode.data, command2.nodes.nodes[definitionIndex].data);
            EXPECT_EQ(command2.wrappedNodes[i].nextNodes.size(), command.wrappedNodes[i].nextNodes.size());
        }
        ASSERT_EQ(command2.startNodes.size(), command.startNodes.size());
        for (size_t i = 0; i < command.startNodes.size(); ++i) {
            if (command.startNodes[i] == CHelper::Node::NodeLF::getInstance()) {
                EXPECT_EQ(command2.startNodes[i], CHelper::Node::NodeLF::getInstance());
            } else {
                const auto index = static_cast<size_t>(command.startNodes[i] - command.wrappedNodes.data());
                EXPECT_EQ(command2.startNodes[i], &command2.wrappedNodes[index]);
            }
        }
    }
}

TEST(BinaryUtilTest, CommandGraphBinaryReferencesAndBounds) {
    using namespace CHelper;
    Node::initializeStaticNodes();
    Node::NodePerCommand source;
    source.name.emplace_back(u"graph");
    source.description = u"graph test";
    source.nodes.nodes.emplace_back(*new Node::NodeInteger("ARG", u"argument", std::nullopt, std::nullopt));
    const auto makeBinary = [&](const std::vector<Node::WrappedNodeWire> &wrapped,
                                const std::vector<std::uint32_t> &starts) {
        std::string buffer(2 * glz::write_padding_bytes, '\0');
        glz::context ctx;
        size_t ix = 0;
        const auto write = [&](const auto &value) {
            glz::serialize<BinaryFormat>::op<glz::opts{}>(value, ctx, buffer, ix);
        };
        write(source.name);
        write(source.description);
        write(source.syntax);
        write(source.nodes);
        write(wrapped);
        write(starts);
        buffer.resize(ix);
        return buffer;
    };
    const std::vector<Node::WrappedNodeWire> wrapped = {
            {0, {1, UINT32_MAX}},
            {0, {2}},
            {0, {0, UINT32_MAX}}};
    const auto binary = makeBinary(wrapped, {0, UINT32_MAX});
    Node::NodePerCommand restored;
    ASSERT_NO_THROW(readBinary(restored, binary));
    ASSERT_EQ(restored.wrappedNodes.size(), 3);
    EXPECT_EQ(restored.wrappedNodes[0].nextNodes[0], &restored.wrappedNodes[1]);
    EXPECT_EQ(restored.wrappedNodes[1].nextNodes[0], &restored.wrappedNodes[2]);
    EXPECT_EQ(restored.wrappedNodes[2].nextNodes[0], &restored.wrappedNodes[0]);
    EXPECT_TRUE(restored.wrappedNodes[0].hasNextLF);
    EXPECT_FALSE(restored.wrappedNodes[1].hasNextLF);
    EXPECT_TRUE(restored.wrappedNodes[2].hasNextLF);
    ASSERT_EQ(restored.startNodes.size(), 2);
    EXPECT_EQ(restored.startNodes[0], &restored.wrappedNodes[0]);
    EXPECT_EQ(restored.startNodes[1], Node::NodeLF::getInstance());
    // 复用对象时也必须重建图，不能残留上一次读取的指针和 LF 缓存。
    auto previousDefinitions = std::move(restored.nodes);
    ASSERT_NO_THROW(readBinary(restored, makeBinary({{0, {}}}, {0})));
    ASSERT_EQ(restored.wrappedNodes.size(), 1);
    EXPECT_TRUE(restored.wrappedNodes[0].nextNodes.empty());
    EXPECT_FALSE(restored.wrappedNodes[0].hasNextLF);
    EXPECT_EQ(restored.startNodes[0], &restored.wrappedNodes[0]);

    for (const auto &invalid: {makeBinary({{-1, {}}}, {0}), makeBinary({{1, {}}}, {0}),
                               makeBinary({{0, {1}}}, {0}), makeBinary({{0, {}}}, {1})}) {
        Node::NodePerCommand value;
        EXPECT_THROW(readBinary(value, invalid), std::runtime_error);
    }
    // 覆盖每个长度/索引字段的截断，包括只剩部分 uint32 的情况。
    for (size_t size = 0; size < binary.size(); ++size) {
        SCOPED_TRACE(size);
        Node::NodePerCommand value;
        EXPECT_THROW(readBinary(value, std::string_view(binary.data(), size)), std::runtime_error);
    }
}

TEST(BinaryUtilTest, FixedWidthVectorBinary) {
    const std::string bytes("\x02\x00\x00\x00\x78\x56\x34\x12\xff\xff\xff\xff", 12);
    std::vector<std::uint32_t> values;
    ASSERT_NO_THROW(CHelper::readBinary(values, bytes));
    EXPECT_EQ(values, (std::vector<std::uint32_t>{0x12345678, UINT32_MAX}));
    for (size_t size = 0; size < bytes.size(); ++size) {
        EXPECT_THROW(CHelper::readBinary(values, std::string_view(bytes.data(), size)), std::runtime_error);
    }
    std::vector<bool> bits{true, false, true};
    std::string binary;
    CHelper::writeBinary(binary, bits);
    std::vector<bool> restored;
    ASSERT_NO_THROW(CHelper::readBinary(restored, binary));
    EXPECT_EQ(restored, bits);
}

TEST(BinaryUtilTest, MapBinaryCountBounds) {
    // 损坏的 map 长度必须正常报读取错误，不能先申请数十 GB 的哈希桶。
    std::unordered_map<std::string, std::uint32_t> map;
    EXPECT_THROW(CHelper::readBinary(map, std::string_view("\xff\xff\xff\xff", 4)), std::runtime_error);
}

TEST(BinaryUtilTest, NodeConstructorOwnsStringViews) {
    // 临时字符串在构造完成后立即释放，节点必须持有自己的副本。
    CHelper::Node::NodeText node(std::string(64, 'x'), std::u16string(64, u'文'), CHelper::NormalId::make(u"value"));
    ASSERT_TRUE(node.id.has_value());
    ASSERT_TRUE(node.description.has_value());
    EXPECT_EQ(*node.id, std::string_view(std::string(64, 'x')));
    EXPECT_EQ(*node.description, std::u16string_view(std::u16string(64, u'文')));
    CHelper::Node::NodeSerializable absent(std::nullopt, std::nullopt, false);
    EXPECT_FALSE(absent.id.has_value());
    EXPECT_FALSE(absent.description.has_value());
    CHelper::Node::NodeSerializable empty(std::string_view{}, std::u16string_view{}, false);
    ASSERT_TRUE(empty.id.has_value());
    ASSERT_TRUE(empty.description.has_value());
    EXPECT_TRUE(empty.id->empty());
    EXPECT_TRUE(empty.description->empty());
}

TEST(BinaryUtilTest, Utf16ConversionPreservesUnicodeAndRejectsInvalidUtf8) {
    const std::vector<std::u16string> cases{
            u"", u"1234567", u"12345678", u"abcdefghijklmnopq", u"中文短描述",
            u"12345678中文", u"mixed 中🙂文", u"🙂🙂🙂🙂", std::u16string(u"a\0中", 3),
            std::u16string(1024, u'a') + u"中🙂文", std::u16string(1024, u'中'),
            u"\u007f\u0080\u07ff\u0800\ud7ff\ue000\uffff\U00010000\U0010ffff"};
    for (const auto &expected: cases) {
        const auto input = utf8::utf16to8(expected);
        std::u16string text = u"old content";
        CHelper::U16Conv::convertToU16(input, text);
        EXPECT_EQ(text, expected);
        std::pmr::u16string pmrText;
        CHelper::U16Conv::convertToU16(input, pmrText);
        EXPECT_EQ(std::u16string_view(pmrText), std::u16string_view(expected));
        // 起始地址偏移一字节，覆盖未对齐的 ASCII 批量检查。
        const auto padded = "x" + input;
        CHelper::U16Conv::convertToU16(std::string_view(padded).substr(1), text);
        EXPECT_EQ(text, expected);
    }
    for (const std::string invalid: {"\x80", "\xc0\xaf", "\xe4\xb8", "\xed\xa0\x80", "\xf4\x90\x80\x80"}) {
        for (const auto &prefix: {std::string{}, std::string(1024, 'a') + utf8::utf16to8(std::u16string_view(u"中🙂文"))}) {
            std::u16string text;
            EXPECT_ANY_THROW(CHelper::U16Conv::convertToU16(prefix + invalid, text));
            std::pmr::u16string pmrText;
            EXPECT_ANY_THROW(CHelper::U16Conv::convertToU16(prefix + invalid, pmrText));
            CHelper::U16Conv::convertToU16("reused", text);
            CHelper::U16Conv::convertToU16("reused", pmrText);
            EXPECT_EQ(text, u"reused");
            EXPECT_EQ(pmrText, u"reused");
        }
    }
}

TEST(BinaryUtilTest, NamespaceIdCacheOwnsQualifiedName) {
    for (const std::optional<std::u16string> prefix: {std::optional<std::u16string>{}, std::optional<std::u16string>{u"custom"}, std::optional<std::u16string>{u""}}) {
        CHelper::NamespaceId id;
        id.name = u"long_block_name";
        if (prefix.has_value()) id.idNamespace.emplace(*prefix);
        id.description = u"说明";
        const auto qualified = id.getIdWithNamespace();
        EXPECT_EQ(std::u16string_view(qualified->name), std::u16string_view(prefix.value_or(u"minecraft") + u":long_block_name"));
        EXPECT_EQ(qualified->description, id.description);
        EXPECT_EQ(id.getIdWithNamespace().get(), qualified.get());
        EXPECT_EQ(id.name, u"long_block_name");
        if (prefix.has_value()) EXPECT_EQ(std::u16string_view(*id.idNamespace), std::u16string_view(*prefix));
    }
}

TEST(BinaryUtilTest, BlockStateDescriptionsKeepPrefixesAndQuotedKeys) {
    using namespace CHelper;
    BlockPropertyDescriptions descriptions;
    auto &definition = descriptions.common.emplace_back();
    definition.type = PropertyType::BOOLEAN;
    definition.propertyName = u"long_property_name";
    definition.description = u"fallback";
    definition.values.emplace_back().valueName.boolean = false;
    auto &trueValue = definition.values.emplace_back();
    trueValue.valueName.boolean = true;
    trueValue.description = u"specific";
    BlockId block;
    block.name = u"test";
    auto &property = block.properties.emplace().emplace_back();
    property.name = definition.propertyName;
    property.defaultValue.boolean = false;
    property.valid.emplace().emplace_back().boolean = true;
    const auto &root = *static_cast<Node::NodeList *>(block.getNode(descriptions).data);
    const auto &allEntries = *static_cast<Node::NodeOr *>(root.nodeElement.data);
    const auto &knownEntries = *static_cast<Node::NodeOr *>(allEntries.childNodes[0].data);
    const auto &entry = *static_cast<Node::NodeEntry *>(knownEntries.childNodes[0].data);
    const auto &key = *static_cast<Node::NodeText *>(entry.nodeKey.data);
    EXPECT_EQ(key.data->name, u"\"long_property_name\"");
    const auto &values = *static_cast<Node::NodeOr *>(entry.nodeValue.data);
    const auto &defaultValue = *static_cast<Node::NodeText *>(values.childNodes[0].data);
    const auto &validValue = *static_cast<Node::NodeText *>(values.childNodes[1].data);
    EXPECT_EQ(defaultValue.data->description, std::optional<std::pmr::u16string>{u"（无效）（默认值）fallback"});
    EXPECT_EQ(validValue.data->description, std::optional<std::pmr::u16string>{u"specific"});
}

TEST(BinaryUtilTest, BlockPropertyIndexPreservesSourceOrder) {
    using namespace CHelper;
    BlockPropertyDescriptions descriptions;
    for (const auto key: {u"p", u"q", u"common", u"p"}) descriptions.common.emplace_back().propertyName = key;
    auto &first = descriptions.block.emplace_back();
    first.blocks = {u"minecraft:test", u"minecraft:test"};
    first.properties.emplace_back().propertyName = u"p";
    auto &second = descriptions.block.emplace_back();
    second.blocks = {u"test", u"minecraft:test"};
    second.properties.emplace_back().propertyName = u"p";
    second.properties.emplace_back().propertyName = u"q";
    auto &third = descriptions.block.emplace_back();
    third.blocks = {u"test"};
    third.properties.emplace_back().propertyName = u"q";
    const BlockPropertyDescriptionIndex index(descriptions);
    for (const auto &[qualified, plain]: {std::pair{u"minecraft:test", u"test"}, std::pair{u"other:unknown", u"unknown"}, std::pair{u"test", u"test"}}) {
        for (const auto key: {u"p", u"q", u"common"}) {
            EXPECT_EQ(&index.getPropertyDescription(qualified, plain, key),
                      &descriptions.getPropertyDescription(qualified, plain, key));
        }
        EXPECT_THROW((void) index.getPropertyDescription(qualified, plain, u"missing"), std::runtime_error);
    }
}

TEST(BinaryUtilTest, SharedBlockPropertyNodesPreserveVariantsAndLifetime) {
    using namespace CHelper;
    BlockPropertyDescriptions descriptions;
    auto &definition = descriptions.common.emplace_back();
    definition.propertyName = u"shared_property";
    definition.description = u"fallback";
    definition.values.emplace_back().valueName.boolean = false;
    definition.values.emplace_back().valueName.boolean = true;
    const auto makeBlock = [&](bool defaultValue, bool emptyValid) {
        auto block = std::make_unique<BlockId>();
        block->name = u"test";
        auto &property = block->properties.emplace().emplace_back();
        property.name = definition.propertyName;
        property.defaultValue.boolean = defaultValue;
        if (emptyValid) property.valid.emplace();
        return block;
    };
    auto first = makeBlock(false, false);
    auto second = makeBlock(false, false);
    auto differentDefault = makeBlock(true, false);
    auto invalid = makeBlock(false, true);
    auto uncached = makeBlock(false, false);
    const auto overrideDefinition = definition;
    const auto entryOf = [&](BlockId &block, BlockPropertyNodeCache *cache) {
        const auto &root = *static_cast<Node::NodeList *>(block.getNode(descriptions, nullptr, cache).data);
        const auto &all = *static_cast<Node::NodeOr *>(root.nodeElement.data);
        const auto &known = *static_cast<Node::NodeOr *>(all.childNodes[0].data);
        return static_cast<const Node::NodeEntry *>(known.childNodes[0].data);
    };
    const auto valueOf = [](const Node::NodeEntry &entry, size_t index) -> const NormalId & {
        const auto &values = *static_cast<const Node::NodeOr *>(entry.nodeValue.data);
        return *static_cast<const Node::NodeText *>(values.childNodes[index].data)->data;
    };
    const Node::NodeEntry *retained;
    {
        BlockPropertyNodeCache cache(descriptions);
        const auto *a = entryOf(*first, &cache);
        retained = entryOf(*second, &cache);
        EXPECT_EQ(a, retained);
        const auto *b = entryOf(*differentDefault, &cache);
        EXPECT_NE(a, b);
        EXPECT_EQ(valueOf(*a, 0).description, std::optional<std::pmr::u16string>{u"（默认值）fallback"});
        EXPECT_EQ(valueOf(*b, 1).description, std::optional<std::pmr::u16string>{u"（默认值）fallback"});
        const auto *c = entryOf(*invalid, &cache);
        EXPECT_NE(a, c);
        EXPECT_EQ(valueOf(*c, 0).description, std::optional<std::pmr::u16string>{u"（无效）（默认值）fallback"});
        const auto *original = entryOf(*uncached, nullptr);
        for (size_t i = 0; i < 2; ++i) {
            EXPECT_EQ(valueOf(*a, i).name, valueOf(*original, i).name);
            EXPECT_EQ(valueOf(*a, i).description, valueOf(*original, i).description);
        }
        // 内容相同的另一条描述仍保留独立身份，避免覆盖项互相污染。
        EXPECT_NE(cache.getNode(overrideDefinition, first->properties->front())->node.data, a);
    }
    first.reset();
    EXPECT_EQ(valueOf(*retained, 0).name, u"false");
    EXPECT_EQ(valueOf(*retained, 1).description, std::optional<std::pmr::u16string>{u"fallback"});
}

TEST(BinaryUtilTest, SharedBlockPropertyCacheComparesValuesByContent) {
    using namespace CHelper;
    for (const auto type: {PropertyType::INTEGER, PropertyType::STRING}) {
        BlockPropertyDescriptions descriptions;
        auto &definition = descriptions.common.emplace_back();
        definition.type = type;
        definition.propertyName = u"property";
        Property property;
        property.type = type;
        property.valid.emplace();
        for (int i = 0; i < 2; ++i) {
            auto &value = definition.values.emplace_back().valueName;
            auto &valid = property.valid->emplace_back();
            if (type == PropertyType::STRING) {
                value.string = new std::pmr::u16string(i == 0 ? u"north" : u"south");
                valid.string = new std::pmr::u16string(*value.string);
            } else {
                value.integer = valid.integer = i;
            }
        }
        if (type == PropertyType::STRING) property.defaultValue.string = new std::pmr::u16string(u"north");
        else
            property.defaultValue.integer = 0;
        const Property copy(property);
        Property different(property);
        if (type == PropertyType::STRING) *different.defaultValue.string = u"south";
        else
            different.defaultValue.integer = 1;
        BlockPropertyNodeCache cache(descriptions);
        const auto first = cache.getNode(definition, property);
        EXPECT_EQ(cache.getNode(definition, copy).get(), first.get());
        EXPECT_NE(cache.getNode(definition, different).get(), first.get());
    }
}
