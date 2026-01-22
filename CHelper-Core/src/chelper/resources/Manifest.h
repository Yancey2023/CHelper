//
// Created by Yancey on 2023/11/7.
//

#pragma once

#ifndef CHELPER_MANIFEST_H
#define CHELPER_MANIFEST_H

#include <pch.h>

namespace CHelper {

    class Manifest {
    public:
        std::optional<std::u16string> name, description, version, versionType, branch, author, updateDate;
        std::u16string packId;
        int32_t versionCode;
        std::optional<bool> isBasicPack, isDefault;
    };

}// namespace CHelper

CODEC(CHelper::Manifest, name, description, version, versionType, branch, author, updateDate, packId, versionCode, isBasicPack, isDefault)

#endif//CHELPER_MANIFEST_H
