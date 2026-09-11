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
#include <chelper/serialization/Serialization.h>
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
        CHelper::Profile::printAndClear(e);
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
        CHelper::Profile::printAndClear(e);
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
        CHelper::Profile::printAndClear(e);
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
        CHelper::Profile::printAndClear(e);
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
        CHelper::Profile::printAndClear(e);
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
        CHelper::Profile::printAndClear(e);
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
        CHelper::Profile::printAndClear(e);
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
        CHelper::Profile::printAndClear(e);
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
