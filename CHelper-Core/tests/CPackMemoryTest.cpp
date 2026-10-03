/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026 Yancey
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include <chelper/CommandContext.h>
#include <chelper/node/CommandNode.h>
#include <chelper/serialization/Serialization.h>
#include <gtest/gtest.h>

namespace CHelper::Test {
    namespace {
        class RecordingResource final : public std::pmr::memory_resource {
        public:
            size_t allocations = 0;
            size_t deallocations = 0;
            std::unordered_map<void *, std::pair<size_t, size_t>> live;

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
            EXPECT_EQ(origin.allocations, 4u);
        }
        {
            RouterScope scope(&other);
            delete text;
            delete copy;
        }
        EXPECT_EQ(origin.deallocations, 4u);
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
