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
