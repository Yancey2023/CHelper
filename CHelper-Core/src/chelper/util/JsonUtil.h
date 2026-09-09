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

#ifndef CHELPER_JSONUTIL_H
#define CHELPER_JSONUTIL_H

#include <string>
#include <string_view>

namespace CHelper {

    class ErrorReason;

    namespace JsonUtil {

        class ConvertResult {
        public:
            std::u16string result;
            std::shared_ptr<ErrorReason> errorReason;
            std::vector<size_t> indexConvertList;
            bool isComplete = false;

            [[nodiscard]] size_t convert(size_t index) const;
        };

        std::u16string string2jsonString(const std::u16string_view &input);

        ConvertResult jsonString2String(const std::u16string_view &input);

        /**
         * 剥离 JSON 中的注释（行注释与块注释，字符串内的标记不处理），
         * 注释替换为单个空格以避免相邻 token 粘连。
         * 用于"资源包 JSON 允许写注释"：包文件可直接内联字段说明。
         */
        std::string stripJsonComments(std::string_view input);

        /**
         * 带注释容错的 JSON 解析：先尝试直接解析（无注释的包零开销），
         * 失败则剥离注释后重试。@return 解析成功且无错误。
         */
        template<class JsonDocument>
        bool parseJsonWithComments(JsonDocument &doc, const char *data, size_t size) {
            if (!doc.Parse(data, size).HasParseError()) {
                return true;
            }
            std::string cleaned = stripJsonComments(std::string_view(data, size));
            doc.Parse(cleaned.data(), cleaned.size());
            return !doc.HasParseError();
        }

    }// namespace JsonUtil

}// namespace CHelper

#endif//CHELPER_JSONUTIL_H
