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

#ifndef CHELPER_CPACK_TEST_HELPER_H
#define CHELPER_CPACK_TEST_HELPER_H

#include <chelper/CHelperCore.h>
#include <chelper/serialization/Serialization.h>
#include <chelper/util/Profile.h>
#include <gtest/gtest.h>

namespace CHelper::Test {

    //测试用的最小CPack模板。
    //id里的数据是TargetSelectorData::init的硬性要求(item/entity命名空间和族/游戏模式/物品栏)，
    //其余部分由各个测试通过参数填充。
    //extraIds是追加到id数组里的额外id对象(不带方括号，多个对象用逗号分隔)
    inline std::string makeCpackJson(const std::string &jsonNodes = "[]",
                                     const std::string &repeatNodes = "[]",
                                     const std::string &commands = "[]",
                                     const std::string &extraIds = "") {
        return R"({
  "manifest": {
    "name": "test pack",
    "description": "pack for unit tests",
    "version": "1.0.0",
    "versionType": "beta",
    "branch": "test",
    "author": "tester",
    "updateDate": "2026-01-01",
    "packId": "test-1.0.0",
    "versionCode": 1,
    "isBasicPack": false,
    "isDefault": false
  },
  "id": [
    {"type": "item", "content": []},
    {"type": "namespace", "id": "entity", "content": []},
    {"type": "normal", "id": "entityFamily", "content": []},
    {"type": "normal", "id": "gameMode", "content": []},
    {"type": "normal", "id": "entitySlot", "content": []})" +
               (extraIds.empty() ? "" : "," + extraIds) +
               R"(
  ],
  "json": )" + jsonNodes +
               R"(,
  "repeat": )" +
               repeatNodes + R"(,
  "command": )" +
               commands + R"(
})";
    }

    /**
     * 尝试从JSON字符串加载CPack，返回是否加载成功
     */
    inline bool tryCreateCpack(const std::string &json, std::unique_ptr<CPack> &out) {
        try {
            out = CHelper::serialization::createCPackByJson(json);
            return true;
        } catch (const std::exception &e) {
            Profile::printAndClear(e);
            return false;
        }
    }

    /**
     * 期望非法的CPack数据在加载阶段被拒绝(fail-fast)，而不是进入Parser
     */
    inline void expectCpackRejected(const std::string &json) {
        std::unique_ptr<CPack> cpack;
        EXPECT_FALSE(tryCreateCpack(json, cpack)) << "invalid cpack was accepted: " << json;
    }

}// namespace CHelper::Test

#endif//CHELPER_CPACK_TEST_HELPER_H
