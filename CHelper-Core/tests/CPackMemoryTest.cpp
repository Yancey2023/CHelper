/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026 Yancey
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include <chelper/CommandContext.h>
#include <chelper/lexer/Lexer.h>
#include <chelper/node/CommandNode.h>
#include <chelper/parser/ErrorReasonFactory.h>
#include <chelper/serialization/Serialization.h>
#include <gtest/gtest.h>

namespace CHelper::Test {
    namespace {
        class RecordingResource final : public std::pmr::memory_resource {
        public:
            size_t allocations = 0;
            size_t deallocations = 0;
            std::unordered_map<void *, std::pair<size_t, size_t>> live;

            [[nodiscard]] bool owns(const void *pointer) const {
                const auto address = reinterpret_cast<std::uintptr_t>(pointer);
                return std::ranges::any_of(live, [address](const auto &entry) {
                    const auto begin = reinterpret_cast<std::uintptr_t>(entry.first);
                    return address >= begin && address - begin < entry.second.first;
                });
            }

        private:
            void *do_allocate(size_t bytes, size_t alignment) override {
                auto *pointer = std::pmr::new_delete_resource()->allocate(bytes, alignment);
                live.emplace(pointer, std::pair{bytes, alignment});
                ++allocations;
                return pointer;
            }

            void do_deallocate(void *pointer, size_t bytes, size_t alignment) override {
                const auto entry = live.find(pointer);
                EXPECT_NE(entry, live.end());
                if (entry == live.end()) return;
                EXPECT_EQ(entry->second, (std::pair{bytes, alignment}));
                live.erase(entry);
                ++deallocations;
                std::pmr::new_delete_resource()->deallocate(pointer, bytes, alignment);
            }

            bool do_is_equal(const std::pmr::memory_resource &other) const noexcept override { return this == &other; }
        };

        class RouterScope {
            std::pmr::memory_resource *previous;

        public:
            explicit RouterScope(std::pmr::memory_resource *resource)
                : previous(CPackMemoryRouter::getCurrent()) { CPackMemoryRouter::setCurrent(resource); }
            ~RouterScope() { CPackMemoryRouter::setCurrent(previous); }
        };

        struct alignas(128) AlignedNode : Node::NodeBase {
            int value = 42;
        };

        struct ThrowingNode : Node::NodeBase {
            ThrowingNode() { throw std::runtime_error("constructor failed"); }
        };
    }// namespace

    TEST(CPackMemoryTest, ErrorListsKeepSingleEntriesInlineAndReleaseArraysThroughTheirOwner) {
        RecordingResource origin, other;
        const auto first = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {0, 1}, u"first");
        const auto second = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {1, 2}, u"second");
        ErrorReasonList list(&origin);
        EXPECT_EQ(list.begin(), list.end());
        list.push_back(first);
        EXPECT_EQ(origin.allocations, 0u);
        EXPECT_EQ(list.front(), first);
        list.push_back(list.front());
        ASSERT_EQ(list.size(), 2u);
        EXPECT_EQ(list[1], first);
        list.push_back(second);
        list.push_back(list[0]);
        list.push_back(list[1]);
        ASSERT_EQ(list.size(), 5u);
        EXPECT_EQ(list[2], second);
        EXPECT_EQ(list[4], first);
        EXPECT_EQ(origin.live.size(), 1u);
        {
            RouterScope scope(&other);
            ErrorReasonList copy(list);
            EXPECT_EQ(copy.get_allocator().resource(), &other);
            EXPECT_TRUE(std::ranges::equal(copy, list));
            const auto allocationCount = other.allocations;
            ErrorReasonList moved(std::move(copy));
            EXPECT_TRUE(copy.empty());
            EXPECT_TRUE(std::ranges::equal(moved, list));
            EXPECT_EQ(other.allocations, allocationCount);
            ErrorReasonList assigned(&origin);
            assigned = std::move(moved);
            EXPECT_TRUE(moved.empty());
            EXPECT_EQ(assigned.get_allocator().resource(), &origin);
            EXPECT_TRUE(std::ranges::equal(assigned, list));
            list.clear();
            EXPECT_FALSE(origin.live.empty());
        }
        EXPECT_TRUE(origin.live.empty());
        EXPECT_TRUE(other.live.empty());
        ErrorReasonList source({first, second}, &origin);
        const auto allocations = origin.allocations;
        list = std::move(source);
        EXPECT_TRUE(source.empty());
        EXPECT_EQ(list.size(), 2u);
        EXPECT_EQ(list[0], first);
        EXPECT_EQ(list[1], second);
        EXPECT_EQ(origin.allocations, allocations);
        list = {second};
        EXPECT_EQ(list.front(), second);
        list.clear();
        EXPECT_TRUE(list.empty());
        EXPECT_EQ(origin.allocations, origin.deallocations);
        EXPECT_EQ(other.allocations, other.deallocations);
    }

    TEST(CPackMemoryTest, ErrorListDestructionReleasesLastOwnersAndUsesItsOriginalResource) {
        for (const size_t count: {0u, 1u, 2u, 5u}) {
            RecordingResource origin, other;
            std::vector<std::weak_ptr<ErrorReason>> weak;
            std::optional<ErrorReasonList> list;
            list.emplace(&origin);
            for (size_t i = 0; i < count; ++i) {
                const auto error = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {i, i + 1}, u"owned error");
                weak.push_back(error);
                list->push_back(error);
            }
            for (const auto &error: weak) EXPECT_FALSE(error.expired());
            {
                RouterScope scope(&other);
                list.reset();
            }
            for (const auto &error: weak) EXPECT_TRUE(error.expired());
            EXPECT_TRUE(origin.live.empty());
            EXPECT_EQ(origin.allocations, origin.deallocations);
            EXPECT_EQ(other.allocations, 0u);
        }
    }

    TEST(CPackMemoryTest, ErrorListsRetainValuesWhenArrayAllocationFails) {
        class FailingResource final : public std::pmr::memory_resource {
            void *do_allocate(size_t, size_t) override { throw std::bad_alloc(); }
            void do_deallocate(void *, size_t, size_t) override {}
            bool do_is_equal(const std::pmr::memory_resource &other) const noexcept override { return this == &other; }
        } resource;
        auto value = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {0, 1}, u"owned diagnostic");
        std::weak_ptr<ErrorReason> weak = value;
        ErrorReasonList list(&resource);
        list.push_back(value);
        value.reset();
        EXPECT_FALSE(weak.expired());
        EXPECT_THROW(list.push_back(list.front()), std::bad_alloc);
        ASSERT_EQ(list.size(), 1u);
        EXPECT_EQ(list.front()->getMessage(), u"owned diagnostic");
        ErrorReasonList multi({list.front(), list.front()});
        EXPECT_THROW(list = multi, std::bad_alloc);
        ASSERT_EQ(list.size(), 1u);
        EXPECT_THROW(list = std::move(multi), std::bad_alloc);
        EXPECT_EQ(multi.size(), 2u);
        multi.clear();
        list.clear();
        EXPECT_TRUE(weak.expired());
    }

    TEST(CPackMemoryTest, ASTArraysRememberTheirAllocationResourceAcrossScopesAndCopies) {
        CPackMemoryRouter::install();
        RecordingResource origin, other;
        const auto lexer = Lexer::lex(u"abc def");
        const auto node = Node::NodeAny::getNodeAny();
        const auto error = ErrorReasons::customText(ErrorReasonLevel::CONTENT_ERROR, {0, 3}, u"测试错误");
        std::optional<ASTNode> tree, copy, heapCopy, cleanTree;
        {
            RouterScope scope(&origin);
            tree.emplace(ASTNode::andNode(node,
                                          ASTNode::children(ASTNode::simpleNode(node, TokensView(lexer, 0, 1), error)),
                                          TokensView(lexer, 0, lexer->allTokens.size())));
            EXPECT_EQ(tree->childNodes.get_allocator().resource(), &origin);
            EXPECT_EQ(tree->errorReasons.get_allocator().resource(), &origin);
            cleanTree.emplace(ASTNode::andNode(node,
                                               ASTNode::children(ASTNode::simpleNode(node, TokensView(lexer, 0, 1))),
                                               TokensView(lexer, 0, lexer->allTokens.size())));
            EXPECT_EQ(cleanTree->errorReasons.get_allocator().resource(), &origin);
            EXPECT_EQ(cleanTree->childNodes[0].childNodes.get_allocator().resource(), &origin);
        }
        heapCopy.emplace(*tree);
        EXPECT_EQ(heapCopy->childNodes.get_allocator().resource(), CPackMemoryRouter::getAllocationResource());
        {
            RouterScope scope(&other);
            copy.emplace(*tree);
            EXPECT_EQ(copy->childNodes.get_allocator().resource(), &other);
            EXPECT_EQ(copy->errorReasons.get_allocator().resource(), &other);
            EXPECT_EQ(copy->childNodes[0].errorReasons.get_allocator().resource(), &other);
            heapCopy.reset();
            tree.reset();
            cleanTree.reset();
            EXPECT_TRUE(origin.live.empty());
            EXPECT_FALSE(other.live.empty());
        }
        {
            RouterScope scope(&origin);
            copy.reset();
        }
        EXPECT_TRUE(origin.live.empty());
        EXPECT_TRUE(other.live.empty());
        EXPECT_EQ(origin.allocations, origin.deallocations);
        EXPECT_EQ(other.allocations, other.deallocations);
    }

    TEST(CPackMemoryTest, NodeAllocationRemembersResourceAndAlignment) {
        RecordingResource origin;
        RecordingResource other;
        AlignedNode *node;
        {
            RouterScope scope(&origin);
            node = new AlignedNode;
            EXPECT_EQ(reinterpret_cast<std::uintptr_t>(node) % alignof(AlignedNode), 0u);
            EXPECT_EQ(node->value, 42);
        }
        {
            RouterScope scope(&other);
            delete node;
        }
        EXPECT_EQ(origin.allocations, 1u);
        EXPECT_EQ(origin.deallocations, 1u);
        EXPECT_TRUE(origin.live.empty());
        EXPECT_EQ(other.allocations, 0u);
        EXPECT_EQ(other.deallocations, 0u);
    }

    TEST(CPackMemoryTest, PropertyStringObjectAndBufferUseOriginResource) {
        RecordingResource origin;
        RecordingResource other;
        PropertyString *text;
        PropertyString *copy;
        {
            RouterScope scope(&origin);
            text = new PropertyString(u"property string longer than inline storage", &origin);
            copy = new PropertyString(*text, &origin);
            EXPECT_EQ(*copy, *text);
            // MSVC Debug 会额外分配迭代器代理；检查实际归属，不依赖标准库的分配次数。
            EXPECT_TRUE(origin.owns(text));
            EXPECT_TRUE(origin.owns(copy));
            EXPECT_TRUE(origin.live.contains(text->data()));
            EXPECT_TRUE(origin.live.contains(copy->data()));
        }
        {
            RouterScope scope(&other);
            delete text;
            delete copy;
        }
        EXPECT_EQ(origin.deallocations, origin.allocations);
        EXPECT_TRUE(origin.live.empty());
        EXPECT_EQ(other.allocations, 0u);
        EXPECT_EQ(other.deallocations, 0u);
    }

    TEST(CPackMemoryTest, PropertyStringConstructorFailureReleasesObject) {
        RecordingResource resource;
        {
            RouterScope scope(&resource);
            EXPECT_THROW((void) new PropertyString(SIZE_MAX, u'x', &resource), std::length_error);
        }
        EXPECT_EQ(resource.allocations, 1u);
        EXPECT_EQ(resource.deallocations, 1u);
        EXPECT_TRUE(resource.live.empty());
    }

    TEST(CPackMemoryTest, DefaultItemNodeOutlivesLoadingPool) {
        Node::NodeWithType retained;
        {
            const auto memory = std::make_shared<CPackMemoryResource>();
            const CPackMemoryScope scope(memory);
            ItemId first;
            ItemId second;
            retained = first.getNode();
            EXPECT_EQ(retained.data, second.getNode().data);
        }
        ItemId third;
        EXPECT_EQ(retained.data, third.getNode().data);
        const auto &integer = *static_cast<const Node::NodeInteger *>(retained.data);
        EXPECT_EQ(integer.id, "ITEM_DATA");
        EXPECT_EQ(integer.description, u"物品附加值");
        EXPECT_EQ(integer.min, -1);
        EXPECT_FALSE(integer.max.has_value());
        ItemId limited;
        limited.max = 5;
        const auto &limitedNode = limited.getNode();
        EXPECT_NE(retained.data, limitedNode.data);
        EXPECT_EQ(static_cast<const Node::NodeInteger *>(limitedNode.data)->max, 5);
        ItemId described;
        described.descriptions.emplace();
        EXPECT_NE(retained.data, described.getNode().data);
        EXPECT_EQ(described.getNode().nodeTypeId, Node::NodeTypeId::OR);
        ItemId invalid;
        invalid.max = -1;
        EXPECT_THROW((void) invalid.getNode(), std::runtime_error);
    }

    TEST(CPackMemoryTest, HeapNodeCanBeDeletedWhilePoolIsActive) {
        RecordingResource resource;
        AlignedNode *node;
        {
            RouterScope scope(nullptr);
            node = new AlignedNode;
        }
        {
            RouterScope scope(&resource);
            delete node;
        }
        EXPECT_TRUE(resource.live.empty());
        EXPECT_EQ(resource.deallocations, 0u);
    }

    TEST(CPackMemoryTest, FailedConstructorReleasesAllocation) {
        RecordingResource resource;
        RouterScope scope(&resource);
        EXPECT_THROW(new ThrowingNode, std::runtime_error);
        EXPECT_EQ(resource.allocations, 1u);
        EXPECT_EQ(resource.deallocations, 1u);
        EXPECT_TRUE(resource.live.empty());
    }

    TEST(CPackMemoryTest, NothrowAndPlacementAllocation) {
        class FailingResource final : public std::pmr::memory_resource {
            void *do_allocate(size_t, size_t) override { throw std::bad_alloc(); }
            void do_deallocate(void *, size_t, size_t) override {}
            bool do_is_equal(const std::pmr::memory_resource &other) const noexcept override { return this == &other; }
        } resource;
        RouterScope scope(&resource);
        EXPECT_EQ(new (std::nothrow) Node::NodeBase, nullptr);
        EXPECT_EQ(new (std::nothrow) AlignedNode, nullptr);
        alignas(AlignedNode) std::byte storage[sizeof(AlignedNode)];
        auto *node = new (storage) AlignedNode;
        EXPECT_EQ(node->value, 42);
        node->~AlignedNode();
    }

    TEST(CPackMemoryTest, LoadAndDestructionRestoreCallerResource) {
        const auto memory = std::make_shared<CPackMemoryResource>();
        CPackMemoryScope scope(memory);
        auto *const original = CPackMemoryRouter::getCurrent();
        const std::filesystem::path resourceDir(RESOURCE_DIR);
        auto first = serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "vanilla");
        ASSERT_NE(first, nullptr);
        EXPECT_EQ(CPackMemoryRouter::getCurrent(), original);
        auto second = serialization::createCPackByDirectory(resourceDir / "resources" / "beta" / "experiment");
        ASSERT_NE(second, nullptr);
        EXPECT_EQ(CPackMemoryRouter::getCurrent(), original);
        {
            CommandContext context(std::shared_ptr<const CPack>(std::move(second)), u"say hello");
            EXPECT_EQ(CPackMemoryRouter::getCurrent(), original);
            EXPECT_EQ(context.getCommand(), u"say hello");
        }
        EXPECT_EQ(CPackMemoryRouter::getCurrent(), original);
        first.reset();
        EXPECT_EQ(CPackMemoryRouter::getCurrent(), original);
        second.reset();
        EXPECT_EQ(CPackMemoryRouter::getCurrent(), original);
    }
}// namespace CHelper::Test
