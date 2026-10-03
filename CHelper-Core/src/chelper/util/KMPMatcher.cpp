#include <chelper/util/KMPMatcher.h>

namespace CHelper {

    KMPMatcher::KMPMatcher(const std::u16string_view &pattern) : pattern(pattern) {
        size_t patternLength = this->pattern.size();
        // 空串与单字符查询无需失败表；常见的短查询直接使用栈内存。
        if (patternLength <= 1) return;
        failure = patternLength < inlineFailure.size() ? inlineFailure.data() : new size_t[patternLength + 1];
        failure[0] = std::u16string::npos;
        size_t j = std::u16string::npos;
        for (size_t i = 0; i < patternLength; i++) {
            while (j != std::u16string::npos && this->pattern[j] != this->pattern[i]) {
                j = failure[j];
            }
            failure[i + 1] = ++j;
        }
    }

    KMPMatcher::~KMPMatcher() {
        if (failure != inlineFailure.data()) delete[] failure;
    }

    size_t KMPMatcher::match(const std::u16string_view text) const {
        if (pattern.empty()) {
            return 0;
        }
        if (pattern.size() == 1) {
            return text.find(pattern.front());
        }
        size_t textLength = text.size();
        size_t patternLength = pattern.size();
        if (textLength < patternLength) return std::u16string::npos;
        size_t k = 0;
        for (size_t j = 0; j < textLength; j++) {
            while (k != std::u16string::npos && pattern[k] != text[j]) {
                k = failure[k];
            }
            if (++k == patternLength) {
                return j - k + 1;
            }
        }
        return std::u16string::npos;
    }

}// namespace CHelper
