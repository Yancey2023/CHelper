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

#include <gtest/gtest.h>

#include <chelper/old2new/Old2New.h>

namespace CHelper::Test {

    TEST(Old2NewTest, Old2New) {
        std::filesystem::path resourceDir(RESOURCE_DIR);
        Old2New::BlockFixData blockFixData =
                Old2New::blockFixDataFromJson(serialization::get_json_from_file(
                        resourceDir / "resources" / "old2new" / "blockFixData.json"));
        std::vector<std::u16string> oldCommands = {
                uR"(execute @e[x=~5] ~~~ detect ~~-1~ stone 0 setblock ~~1~ command_block 0)",
                uR"(execute @e[type=zombie] ~ ~ ~ summon lightning_bolt)",
                uR"(execute @e[type=zombie] ~ ~ ~ detect ~ ~-1 ~ minecraft:sand -1 summon lightning_bolt)",
                uR"(execute @e[c=10] ~ ~ ~ execute @p ~ ~ ~ summon creeper ~ ~ ~)",
                uR"(execute Yancey ~ ~ ~ summon ender_dragon)",
                uR"(execute @e[x=~5] ~~~ detect ~~-1~ stone 1 setblock ~~1~ stone 2)",
                uR"(setblock ~~~ stone)",
                uR"(setblock ~~~ minecraft:command_block 1)",
                uR"(setblock ~~~ stone 3)",
                uR"(setblock ~~~ stone 3 replace)",
                uR"(fill ~~~~~~ stone)",
                uR"(fill ~~~~~~ stone 4)",
                uR"(fill ~~~~~~ stone 4 hollow)",
                uR"(fill ~~~~~~ stone 4 replace stone)",
                uR"(fill ~~~~~~ stone 4 replace stone 5)",
                uR"(testforblock ~~~ stone)",
                uR"(testforblock ~~~ stone 3)",
                uR"(testforblock ~~~ stone 3 replace)",
                uR"(/execute @e[name="Yancey NB"] ~~2.5 ~ detect ~~-1~ stone 1 /setblock ~ ~-1 ~ command_block 0)",
                uR"(execute @a[tag=!OP] ~~~ detect ~~0.05~0.3 air 0 execute @s ~~~ detect ~-0.3~-0.05~ air 0 execute @s ~~~ detect ~~-0.05~0.3 air 0 execute @s ~~~ detect ~0.3~-0.05~0.3 air 0 execute @s ~~~ detect ~-0.3~-0.05~-0.3 air 0 execute @s ~~~ detect ~0.3~-0.05~-0.3 air 0 execute @s ~~~ detect ~-0.3~-0.05~0.3 air 0 scoreboard players add @s fly 1)",
                uR"(summon creeper ~ ~ ~ minecraft:become_charged "充能苦力怕")",
                uR"(structure load aaa 0 0 0 0_degrees none true true 0.5 aaa)",
                uR"(setblock ~~~ acacia_door["direction":1])",
        };
        for (const auto &item: oldCommands) {
            SPDLOG_INFO("{}", styled(utf8::utf16to8(item), fg(fmt::color::red)));
            SPDLOG_INFO("{}", styled(utf8::utf16to8(Old2New::old2new(blockFixData, item)), fg(fmt::color::lime_green)));
        }
    }

}// namespace CHelper::Test