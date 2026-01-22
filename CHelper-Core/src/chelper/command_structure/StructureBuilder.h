//
// Created by Yancey on 2024/2/21.
//

#pragma once

#ifndef CHELPER_STRUCTUREBUILDER_H
#define CHELPER_STRUCTUREBUILDER_H

#include <pch.h>

namespace CHelper::CommandStructure {

    class StructureBuilder {
    private:
        std::u16string structure;

    public:
        StructureBuilder &appendUnknown(bool isMustHave);

        StructureBuilder &appendSymbol(char16_t ch);

        StructureBuilder &append(const std::u16string &str);

        StructureBuilder &appendSpace();

        StructureBuilder &appendLeftBracket(bool isMustHave);

        StructureBuilder &appendRightBracket(bool isMustHave);

        StructureBuilder &append(bool isMustHave, const std::u16string &str);

        std::u16string build();
    };

}// namespace CHelper::CommandStructure

#endif//CHELPER_STRUCTUREBUILDER_H
