/**
 * It is part of CHelper. CHelper is a command helper for Minecraft Bedrock Edition.
 * Copyright (C) 2026  Akanyi
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

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * 物品 / 槽位 / 族类型查询库。
 * items.json、slots.json 来自网页版 static/data，打包在 assets/rawtext 下。
 * 族类型条目较少，直接内联，省去再拉一个文件。
 */
data class NamedEntry(val id: String, val name: String)

object RawtextDataRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var items: List<NamedEntry>? = null

    @Volatile
    private var slots: List<NamedEntry>? = null

    suspend fun loadItems(context: Context): List<NamedEntry> {
        items?.let { return it }
        return withContext(Dispatchers.IO) {
            val loaded = readMap(context, "rawtext/items.json")
            items = loaded
            loaded
        }
    }

    suspend fun loadSlots(context: Context): List<NamedEntry> {
        slots?.let { return it }
        return withContext(Dispatchers.IO) {
            val loaded = readMap(context, "rawtext/slots.json")
            slots = loaded
            loaded
        }
    }

    private fun readMap(context: Context, path: String): List<NamedEntry> = try {
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        json.parseToJsonElement(text).jsonObject.entries.map { (id, value) ->
            NamedEntry(id, value.jsonPrimitive.contentOrNull ?: id)
        }
    } catch (_: Exception) {
        emptyList()
    }

    val familyTypes: List<NamedEntry> = listOf(
        NamedEntry("armor_stand", "盔甲架"),
        NamedEntry("arrow", "箭"),
        NamedEntry("axolotl", "美西螈"),
        NamedEntry("bat", "蝙蝠"),
        NamedEntry("bee", "蜜蜂"),
        NamedEntry("blaze", "烈焰人"),
        NamedEntry("boat", "船"),
        NamedEntry("cat", "猫"),
        NamedEntry("cave_spider", "洞穴蜘蛛"),
        NamedEntry("chicken", "鸡"),
        NamedEntry("cod", "鳕鱼"),
        NamedEntry("cow", "牛"),
        NamedEntry("creeper", "苦力怕"),
        NamedEntry("dolphin", "海豚"),
        NamedEntry("donkey", "驴"),
        NamedEntry("dragon", "末影龙"),
        NamedEntry("drowned", "溺尸"),
        NamedEntry("egg", "鸡蛋"),
        NamedEntry("elder_guardian", "远古守卫者"),
        NamedEntry("ender_crystal", "末地水晶"),
        NamedEntry("ender_pearl", "末影珍珠"),
        NamedEntry("enderman", "末影人"),
        NamedEntry("endermite", "末影螨"),
        NamedEntry("evocation_illager", "唤魔者"),
        NamedEntry("eye_of_ender", "末影之眼"),
        NamedEntry("falling_block", "掉落方块"),
        NamedEntry("fireball", "火球"),
        NamedEntry("fireworks_rocket", "烟花火箭"),
        NamedEntry("fishing_hook", "钓鱼钩"),
        NamedEntry("fox", "狐狸"),
        NamedEntry("ghast", "恶魂"),
        NamedEntry("glow_squid", "发光鱿鱼"),
        NamedEntry("goat", "山羊"),
        NamedEntry("guardian", "守卫者"),
        NamedEntry("hoglin", "疣猪兽"),
        NamedEntry("horse", "马"),
        NamedEntry("husk", "尸壳"),
        NamedEntry("iron_golem", "铁傀儡"),
        NamedEntry("item", "物品"),
        NamedEntry("leash_knot", "拴绳结"),
        NamedEntry("lightning_bolt", "闪电"),
        NamedEntry("llama", "羊驼"),
        NamedEntry("magma_cube", "岩浆怪"),
        NamedEntry("minecart", "矿车"),
        NamedEntry("mooshroom", "哞菇"),
        NamedEntry("mule", "骡"),
        NamedEntry("npc", "NPC"),
        NamedEntry("ocelot", "豹猫"),
        NamedEntry("panda", "熊猫"),
        NamedEntry("parrot", "鹦鹉"),
        NamedEntry("phantom", "幻翼"),
        NamedEntry("pig", "猪"),
        NamedEntry("piglin", "猪灵"),
        NamedEntry("piglin_brute", "猪灵蛮兵"),
        NamedEntry("pillager", "掠夺者"),
        NamedEntry("player", "玩家"),
        NamedEntry("polar_bear", "北极熊"),
        NamedEntry("pufferfish", "河豚"),
        NamedEntry("rabbit", "兔子"),
        NamedEntry("ravager", "劫掠兽"),
        NamedEntry("salmon", "鲑鱼"),
        NamedEntry("sheep", "绵羊"),
        NamedEntry("shulker", "潜影贝"),
        NamedEntry("silverfish", "蠹虫"),
        NamedEntry("skeleton", "骷髅"),
        NamedEntry("skeleton_horse", "骷髅马"),
        NamedEntry("slime", "史莱姆"),
        NamedEntry("snow_golem", "雪傀儡"),
        NamedEntry("spider", "蜘蛛"),
        NamedEntry("splash_potion", "喷溅药水"),
        NamedEntry("squid", "鱿鱼"),
        NamedEntry("stray", "流浪者"),
        NamedEntry("strider", "炽足兽"),
        NamedEntry("tnt", "TNT"),
        NamedEntry("tnt_minecart", "TNT矿车"),
        NamedEntry("trader_llama", "行商羊驼"),
        NamedEntry("tripod_camera", "三脚架相机"),
        NamedEntry("tropicalfish", "热带鱼"),
        NamedEntry("turtle", "海龟"),
        NamedEntry("vex", "恼鬼"),
        NamedEntry("villager", "村民"),
        NamedEntry("vindicator", "卫道士"),
        NamedEntry("wandering_trader", "流浪商人"),
        NamedEntry("warden", "监守者"),
        NamedEntry("witch", "女巫"),
        NamedEntry("wither", "凋灵"),
        NamedEntry("wither_skeleton", "凋灵骷髅"),
        NamedEntry("wolf", "狼"),
        NamedEntry("xp_bottle", "附魔之瓶"),
        NamedEntry("xp_orb", "经验球"),
        NamedEntry("zoglin", "僵尸疣猪兽"),
        NamedEntry("zombie", "僵尸"),
        NamedEntry("zombie_horse", "僵尸马"),
        NamedEntry("zombie_pigman", "僵尸猪灵"),
        NamedEntry("zombie_villager", "僵尸村民"),
    )
}
