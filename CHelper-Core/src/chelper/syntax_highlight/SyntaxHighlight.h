//
// Created by Yancey on 2025-06-20.
//

#pragma once

#ifndef CHELPER_SYNTAXHIGHTLIGHT_H
#define CHELPER_SYNTAXHIGHTLIGHT_H

#include <chelper/parser/ASTNode.h>
#include <chelper/syntax_highlight/SyntaxResult.h>

namespace CHelper::SyntaxHighlight {

    SyntaxResult getSyntaxResult(const ASTNode& astNode);

}// namespace CHelper::SyntaxHighlight

#endif//CHELPER_SYNTAXHIGHTLIGHT_H
