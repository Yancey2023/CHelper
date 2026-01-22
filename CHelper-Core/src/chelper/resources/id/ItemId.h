//
// Created by Yancey on 2023/11/8.
//

#pragma once

#ifndef CHELPER_ITEMID_H
#define CHELPER_ITEMID_H

#include <chelper/node/NodeWithType.h>
#include <chelper/resources/id/NamespaceId.h>
#include <../../../../pch.h>

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
