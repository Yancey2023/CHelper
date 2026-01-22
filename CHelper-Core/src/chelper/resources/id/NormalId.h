//
// Created by Yancey on 2023/11/7.
//

#pragma once

#ifndef CHELPER_NORMALID_H
#define CHELPER_NORMALID_H

#include <../../../../pch.h>

namespace CHelper {

    class NormalId {
    public:
        std::u16string name;
        std::optional<std::u16string> description;

    private:
        XXH64_hash_t nameHash = 0;
        std::optional<XXH3_state_t> hashState;

    public:
        NormalId() = default;

        virtual ~NormalId() = default;

        void buildHash();

        [[nodiscard]] bool fastMatch(XXH64_hash_t strHash);

        [[nodiscard]] XXH3_state_t *getHashState();

        static std::shared_ptr<NormalId> make(const std::u16string &name, const std::optional<std::u16string> &description);
    };

}// namespace CHelper

CODEC(CHelper::NormalId, name, description)

#endif//CHELPER_NORMALID_H
