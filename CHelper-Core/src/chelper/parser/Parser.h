//
// Created by Yancey on 2023/11/6.
//

#pragma once

#ifndef CHELPER_PARSER_H
#define CHELPER_PARSER_H

#include <chelper/parser/ASTNode.h>
#include <chelper/resources/CPack.h>
#include <pch.h>

namespace CHelper::Parser {

    ASTNode parse(const std::u16string &content, const Node::NodeWithType &mainNode);

    ASTNode parse(const std::u16string &content, const CPack &cpack);

}// namespace CHelper::Parser

#endif//CHELPER_PARSER_H
