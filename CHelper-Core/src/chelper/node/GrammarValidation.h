#pragma once

#include <chelper/node/CommandNode.h>

namespace CHelper::Node {
    // Reject empty choices and recursion that can repeat without consuming input.
    void validateGrammar(const NodeJsonElement &grammar);
}// namespace CHelper::Node
