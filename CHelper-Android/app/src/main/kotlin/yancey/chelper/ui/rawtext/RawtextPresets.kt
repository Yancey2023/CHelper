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

package yancey.chelper.ui.rawtext

/** 预设示例 */
object RawtextPresets {

    data class RawtextPresetItem(val name: String, val desc: String, val build: () -> List<RawtextElement>)

    val all: List<RawtextPresetItem> = listOf(
        RawtextPresetItem("彩色欢迎语", "带 § 颜色的文字", { listOf(RawtextElement.Text("§6§l欢迎回来，§r§e冒险家！§r")) }),
        RawtextPresetItem("玩家击杀数", "文字 + 计分板分数", { listOf(RawtextElement.Text("§b你的击杀数：§r"), RawtextElement.Score("@s", "kills")) }),
        RawtextPresetItem(
            "翻译文本（带参数）",
            "翻译 + 4 个参数",
            { listOf(RawtextElement.Translate("commands.tp.success.coordinates", RawtextWithMode.Array, mutableListOf("Steve", "100", "64", "200"), mutableListOf())) }
        ),
        RawtextPresetItem("在线玩家", "文字 + 选择器", { listOf(RawtextElement.Text("§a当前在线玩家：§r"), RawtextElement.Selector("@a")) }),
        RawtextPresetItem("条件菜单", "会员/普通二选一（%%2 条件）", { RawtextJson.importText(conditionJson) }),
        RawtextPresetItem("顺序拼接", "%%1%%2… 条件栏样式", { RawtextJson.importText(concatJson) }),
        RawtextPresetItem("物品检测", "hasitem 检测物品", { RawtextJson.importText(hasitemJson) }),
        RawtextPresetItem("全参数选择器", "所有选择器参数", { RawtextJson.importText(allargsJson) }),
        RawtextPresetItem("翻译识别符", "本地化键（中文显示）", { RawtextJson.importText(langkeyJson) }),
    )

    private val conditionJson = """{"rawtext":[{"translate":"%%2","with":{"rawtext":[{"selector":"@s[tag=会员,scores={菜单=1..}]"},{"text":"§a§l会员菜单\n§e━━━━━━━━\n§f① 领取礼包\n② 传送主城\n③ 关闭菜单\n§7当前身份：会员"},{"text":"§7§l普通菜单\n§e━━━━━━━━\n§f① 领取礼包\n② 传送主城\n§7当前身份：普通玩家"}]}}]}"""

    private val concatJson = """{"rawtext":[{"translate":"%%1%%2%%3%%4","with":{"rawtext":[{"translate":"%%2","with":{"rawtext":[{"selector":"@s[scores={雪球菜单=1}]"},{"text":"§e"},{"text":"§r"}]}},{"text":"① 传送主城"},{"translate":"%%2","with":{"rawtext":[{"selector":"@s[scores={雪球菜单=2}]"},{"text":"§e"},{"text":"§r"}]}},{"text":"\n② 经济商店"}]}}]}"""

    private val hasitemJson = """{"rawtext":[{"selector":"@a[hasitem={item=diamond,quantity=1..}]"},{"text":"§a✔ 你拥有钻石！§r"}]}"""

    private val allargsJson = """{"rawtext":[{"selector":"@a[name=Steve,type=player,m=survival,r=10,rm=1,l=30,lm=5,x=0,y=64,z=0,dx=100,dy=50,dz=100,rx=80,rxm=-80,ry=90,rym=-90]"},{"text":"§b范围内的生存玩家！§r"}]}"""

    private val langkeyJson = """{"rawtext":[{"translate":"item.diamond.name"},{"text":"：§a很值钱！§r"},{"translate":"entity.zombie.name"},{"text":" §7正在靠近…§r"}]}"""
}
