//
// Created by Yancey on 2025-06-20.
//

#pragma once

#ifndef CHELPER_COMMANDSTRUCTURE_H
#define CHELPER_COMMANDSTRUCTURE_H

#include <chelper/parser/ASTNode.h>

namespace CHelper::CommandStructure {

    std::u16string getStructure(const ASTNode &astNode);

}// namespace CHelper::CommandStructure

#endif//CHELPER_COMMANDSTRUCTURE_H
