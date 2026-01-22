//
// Created by Yancey on 2023/11/6.
//

#pragma once

#ifndef CHELPER_LEXER_H
#define CHELPER_LEXER_H

#include <chelper/lexer/LexerResult.h>
#include <pch.h>

namespace CHelper::Lexer {

    LexerResult lex(const std::u16string &content);

}// namespace CHelper::Lexer

#endif//CHELPER_LEXER_H
