//
// Created by Yancey on 2023/11/7.
//

#pragma once

#ifndef CHELPER_JSONUTIL_H
#define CHELPER_JSONUTIL_H

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

    }// namespace JsonUtil

}// namespace CHelper

#endif//CHELPER_JSONUTIL_H
