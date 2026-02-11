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

#include <chelper/parser/ErrorReason.h>

namespace CHelper {

    namespace ErrorReasonLevel {

        ErrorReasonLevel maxLevel = ID_ERROR;

    }// namespace ErrorReasonLevel

    ErrorReason::ErrorReason(ErrorReasonLevel::ErrorReasonLevel level,
                             size_t start,
                             size_t end,
                             std::u16string errorReason)
        : level(level),
          start(start),
          end(end),
          errorReason(std::move(errorReason)) {}

    ErrorReason::ErrorReason(ErrorReasonLevel::ErrorReasonLevel level,
                             const TokensView &tokens,
                             std::u16string errorReason)
        : level(level),
          start(tokens.startIndex),
          end(tokens.endIndex),
          errorReason(std::move(errorReason)) {}

    bool ErrorReason::operator==(const ErrorReason &reason) const {
        return start == reason.start &&
               end == reason.end &&
               errorReason == reason.errorReason;
    }

}// namespace CHelper
