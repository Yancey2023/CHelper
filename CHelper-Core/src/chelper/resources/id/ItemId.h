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

#pragma once

#ifndef CHELPER_ITEMID_H
#define CHELPER_ITEMID_H

#include <chelper/node/NodeWithType.h>
#include <chelper/resources/id/NamespaceId.h>
#include <pch.h>

namespace CHelper {

    class ItemId : public NamespaceId {
    public:
        std::optional<int32_t> max;
        std::optional<std::vector<std::u16string>> descriptions;

    private:
        Node::FreeableNodeWithTypes nodeChildren;
        std::unique_ptr<Node::NodeWithType> node;

    public:
        ItemId() = default;

        const Node::NodeWithType &getNode();
    };

}// namespace CHelper

CODEC_WITH_PARENT(CHelper::ItemId, CHelper::NamespaceId, max, descriptions)

#endif//CHELPER_ITEMID_H
