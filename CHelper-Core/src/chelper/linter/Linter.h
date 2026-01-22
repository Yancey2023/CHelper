//
// Created by Yancey on 2025-06-20.
//

#pragma once

#ifndef CHELPER_LINTER_H
#define CHELPER_LINTER_H

#include <chelper/parser/ASTNode.h>
#include <pch.h>

namespace CHelper::Linter {

    std::vector<std::shared_ptr<ErrorReason>> getErrorsExceptParseError(const ASTNode &astNode);

    std::vector<std::shared_ptr<ErrorReason>> getErrorReasons(const ASTNode &astNode);

}// namespace CHelper::Linter

#endif //CHELPER_LINTER_H
