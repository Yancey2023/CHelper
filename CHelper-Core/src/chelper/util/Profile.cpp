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

#include <chelper/util/Profile.h>
#include <chelper/util/StringUtil.h>

namespace CHelper::Profile {

#ifndef CHELPER_NO_FILESYSTEM
    std::vector<std::string> stack;
#endif

    void pop() {
#ifndef CHELPER_NO_FILESYSTEM
        if (stack.empty()) {
            SPDLOG_ERROR("pop stack when stack is empty");
            return;
        }
        stack.pop_back();
#endif
    }

    void clear() {
#ifndef CHELPER_NO_FILESYSTEM
        stack.clear();
#endif
    }

    void printAndClear(const std::exception &e) {
#ifndef CHELPER_NO_FILESYSTEM
        SPDLOG_ERROR("{}\nstack trace:\n{}", e.what(), StringUtil::join(stack, "\n"));
        stack.clear();
#endif
    }

}// namespace CHelper::Profile