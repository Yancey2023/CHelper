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
#include <chelper/node/NodeType.h>
#include <chelper/node/NodeWithType.h>

namespace CHelper::Node {

    void initializeStaticNodes() {
        static const bool initialized = [] {
            // 即使首个 CPack 在其他内存作用域中加载，静态节点也必须由全局堆持有。
            struct GlobalMemoryScope {
                std::pmr::memory_resource *previous = CPackMemoryRouter::getCurrent();
                GlobalMemoryScope() { CPackMemoryRouter::setCurrent(nullptr); }
                ~GlobalMemoryScope() { CPackMemoryRouter::setCurrent(previous); }
            } scope;
            (void) NodeLF::getInstance();
            (void) NodeAny::getNodeAny();
            (void) NodeJsonElement::getNodeJsonElement();
            (void) NodeJsonEntry::getNodeJsonAllEntry();
            return true;
        }();
        (void) initialized;
    }

    FreeableNodeWithTypes::~FreeableNodeWithTypes() {
        for (auto &item: nodes) {
            if (item.data == nullptr) {
                continue;
            }
            Node::dispatchNodeType(
                    item.nodeTypeId,
                    [&]<class NodeType>() { delete static_cast<NodeType *>(item.data); },
                    [] {
                        //非法类型 id 不属于任何节点类型，旧实现同样直接跳过释放
                    });
            item.data = nullptr;
        }
    }

}// namespace CHelper::Node
