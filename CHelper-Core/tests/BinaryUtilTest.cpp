//
// Created by Yancey on 2024-5-21.
//

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

template<class T, bool isConvertEndian>
void test(const std::function<T()> &getInstance) {
    std::ostringstream oss;
    T t1 = getInstance();
    serialization::Codec<T>::template to_binary<isConvertEndian>(oss, t1);
    std::istringstream iss(oss.str());
    T t2;
    serialization::Codec<T>::template from_binary<isConvertEndian>(iss, t2);
    EXPECT_EQ(t1, t2);
    EXPECT_FALSE(iss.eof());
}

template<class T>
void test(const std::function<T()> &getInstance) {
    test<T, true>(getInstance);
    test<T, false>(getInstance);
}

template<class T, bool isConvertEndian>
void testNode(CHelper::CPack &cpack, const std::function<T()> &getInstance) {
    std::ostringstream oss;
    T t1 = getInstance();
    serialization::Codec<T>::template to_binary<isConvertEndian>(oss, t1);
    std::istringstream iss(oss.str());
    T t2;
    serialization::Codec<T>::template from_binary<isConvertEndian>(iss, t2);
    CHelper::Node::initNode(t2, cpack);
    EXPECT_EQ(t1, t2);
    EXPECT_FALSE(iss.eof());
}

template<class T>
void testNode(CHelper::CPack &cpack, const std::function<T()> &getInstance) {
    testNode<T, true>(cpack, getInstance);
    testNode<T, false>(cpack, getInstance);
}

template<class T>
void test(const std::vector<std::function<T()>> &getInstance) {
    for (const auto &item: getInstance) {
        test(item);
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
    rapidjson::GenericDocument<rapidjson::UTF8<>> j = serialization::get_json_from_file(
            resourceDir / "resources" / "beta" / "vanilla" / "id" / "entity.json");
    std::vector<std::function<CHelper::NamespaceId()>> getInstance;
    for (const auto &item: serialization::find_array_member_or_throw(j, "content")) {
        CHelper::NamespaceId namespaceId;
        serialization::Codec<CHelper::NamespaceId>::from_json(item, namespaceId);
        getInstance.emplace_back([namespaceId]() { return namespaceId; });
    }
    test<CHelper::NamespaceId>(getInstance);
}

TEST(BinaryUtilTest, ItemId) {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    rapidjson::GenericDocument<rapidjson::UTF8<>> j = serialization::get_json_from_file(
            resourceDir / "resources" / "beta" / "vanilla" / "id" / "item.json");
    std::vector<std::function<CHelper::ItemId()>> getInstance;
    for (const auto &item: serialization::find_array_member_or_throw(j, "content")) {
        getInstance.emplace_back([&item]() {
            CHelper::ItemId itemId;
            serialization::Codec<CHelper::ItemId>::from_json(item, itemId);
            return itemId;
        });
    }
    test<CHelper::ItemId>(getInstance);
}

TEST(BinaryUtilTest, Property) {
    auto getInstance1 = []() {
        CHelper::Property aProperty;
        aProperty.name = u"name1";
        aProperty.type = CHelper::PropertyType::BOOLEAN;
        aProperty.defaultValue.boolean = true;
        aProperty.valid = std::vector<CHelper::PropertyValue>(2);
        aProperty.valid->at(0).boolean = true;
        aProperty.valid->at(1).boolean = false;
        return aProperty;
    };
    auto getInstance2 = []() {
        CHelper::Property aProperty;
        aProperty.name = u"name2";
        aProperty.type = CHelper::PropertyType::INTEGER;
        aProperty.defaultValue.integer = 0;
        aProperty.valid = std::vector<CHelper::PropertyValue>(3);
        aProperty.valid->at(0).integer = 0;
        aProperty.valid->at(1).integer = 1;
        aProperty.valid->at(2).integer = 2;
        return aProperty;
    };
    auto getInstance3 = []() {
        CHelper::Property aProperty;
        aProperty.name = u"name3";
        aProperty.type = CHelper::PropertyType::STRING;
        aProperty.defaultValue.string = new std::u16string(u"aaa");
        aProperty.valid = std::vector<CHelper::PropertyValue>(3);
        aProperty.valid->at(0).string = new std::u16string(u"a1");
        aProperty.valid->at(1).string = new std::u16string(u"a2");
        aProperty.valid->at(2).string = new std::u16string(u"a3");
        return aProperty;
    };
    test<CHelper::Property>({getInstance1, getInstance2, getInstance3});
}

TEST(BinaryUtilTest, BlockId) {
    std::filesystem::path resourceDir(RESOURCE_DIR);
    rapidjson::GenericDocument<rapidjson::UTF8<>> j = serialization::get_json_from_file(
            resourceDir / "resources" / "beta" / "vanilla" / "id" / "block.json");
    CHelper::BlockIds blockIds;
    serialization::Codec<CHelper::BlockIds>::from_json(serialization::find_member_or_throw(j, "content"), blockIds);
    test<CHelper::BlockIds>([&blockIds]() { return blockIds; });
}

TEST(BinaryUtilTest, PerCPackNormalIds) {

    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::CPack::createByDirectory(
                std::filesystem::path(resourceDir / "resources" / "beta" / "vanilla"));
    } catch (const std::exception &e) {
        CHelper::Profile::printAndClear(e);
        exit(-1);
    }
    std::vector<std::function<
            std::shared_ptr<std::vector<std::shared_ptr<CHelper::NormalId>>>()>>
            getInstance;
    for (const auto &item: cpack->normalIds) {
        const auto &ids = item.second;
        getInstance.emplace_back([&ids]() { return ids; });
    }
    test<std::shared_ptr<std::vector<std::shared_ptr<CHelper::NormalId>>>>(getInstance);
}

TEST(BinaryUtilTest, CPackNormalIds) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::CPack::createByDirectory(resourceDir / "resources" /
                                                  "beta" / "vanilla");
    } catch (const std::exception &e) {
        CHelper::Profile::printAndClear(e);
        exit(-1);
    }
    test<std::unordered_map<std::string, std::shared_ptr<std::vector<std::shared_ptr<CHelper::NormalId>>>>>(
            [&cpack]() { return cpack->normalIds; });
}

TEST(BinaryUtilTest, CPackNamespaceId) {
    std::unique_ptr<CHelper::CPack> cpack;
    try {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        cpack = CHelper::CPack::createByDirectory(resourceDir / "resources" /
                                                  "beta" / "vanilla");
    } catch (const std::exception &e) {
        CHelper::Profile::printAndClear(e);
        exit(-1);
    }
    test<std::unordered_map<
            std::string,
            std::shared_ptr<std::vector<std::shared_ptr<CHelper::NamespaceId>>>>>(
            [&cpack]() { return cpack->namespaceIds; });
}

TEST(BinaryUtilTest, NodeJsonBoolean) {
    std::unique_ptr<CHelper::CPack> cpack;
    std::filesystem::path resourceDir(RESOURCE_DIR);
    try {
        cpack = CHelper::CPack::createByDirectory(resourceDir / "resources" /
                                                  "beta" / "vanilla");
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
        cpack = CHelper::CPack::createByDirectory(resourceDir / "resources" /
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
        cpack = CHelper::CPack::createByDirectory(resourceDir / "resources" /
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
        cpack = CHelper::CPack::createByDirectory(resourceDir / "resources" / "beta" / "vanilla");
    } catch (const std::exception &e) {
        CHelper::Profile::printAndClear(e);
        exit(-1);
    }
    testNode<CHelper::Node::NodeJsonNull>(
            *cpack, []() { return CHelper::Node::NodeJsonNull{"ID", u"description"}; });
}
