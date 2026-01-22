//
// Created by Yancey on 2024/2/21.
//

#pragma once

#ifndef CHELPER_SUGGESTION_H
#define CHELPER_SUGGESTION_H

#include <chelper/parser/TokensView.h>
#include <chelper/resources/id/NormalId.h>

namespace CHelper {

    class CHelperCore;

    namespace AutoSuggestion {

        class Suggestion {
        public:
            //要被替换的位置
            size_t start, end;
            //如果光标在最后面，是否需要在最后加空格
            bool isAddSpace;
            //内容
            std::shared_ptr<NormalId> content;

        public:
            Suggestion(size_t start, size_t end, bool isAddSpace, const std::shared_ptr<NormalId> &content);

            Suggestion(const TokensView &tokens, bool isAddSpace, const std::shared_ptr<NormalId> &content);

            [[nodiscard]] XXH64_hash_t hashCode() const;
        };

    }// namespace AutoSuggestion

}// namespace CHelper

#endif//CHELPER_SUGGESTION_H
