//
// Created by Yancey on 2023/11/11.
//

#pragma once

#ifndef CHELPER_NAMESPACEID_H
#define CHELPER_NAMESPACEID_H

#include <chelper/resources/id/NormalId.h>
#include <../../../../pch.h>

namespace CHelper {

    class NamespaceId : public NormalId {
    public:
        std::optional<std::u16string> idNamespace;

    private:
        std::shared_ptr<NormalId> idWithNamespace;

    public:
        std::shared_ptr<NormalId> &getIdWithNamespace();
    };

}// namespace CHelper

CODEC_WITH_PARENT(CHelper::NamespaceId, CHelper::NormalId, idNamespace)

#endif//CHELPER_NAMESPACEID_H
