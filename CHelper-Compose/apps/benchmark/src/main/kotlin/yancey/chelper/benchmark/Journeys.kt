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

package yancey.chelper.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

const val TARGET_PACKAGE = "yancey.chelper.benchmark"
private const val HOME = "yancey.chelper.android.activity.HomeActivity"

fun UiDevice.requireText(text: String): UiObject2 =
    checkNotNull(wait(Until.findObject(By.text(text)), 15_000)) { "Missing benchmark text: $text" }

fun UiDevice.requireTag(tag: String): UiObject2 =
    checkNotNull(wait(Until.findObject(By.res(tag)), 15_000)) { "Missing benchmark tag: $tag" }

fun MacrobenchmarkScope.home() {
    // Bounded accessibility idling: a blinking editor caret must not stall each action for 10 s.
    Configurator.getInstance().waitForIdleTimeout = 1_000
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN)
            .setClassName(TARGET_PACKAGE, HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    // Only the isolated fixture application is addressed by this suite.
    device.waitForIdle(1_000)
    if (device.hasObject(By.text("我已阅读并同意。"))) {
        checkNotNull(device.findObject(By.checkable(true))) { "Missing isolated policy switch" }
            .click()
        device.requireText("确认").click()
    }
    device.waitForIdle(1_000)
    device.findObject(By.text("不用了"))?.click()
    device.requireTag("home-actions")
}

fun MacrobenchmarkScope.settings() {
    device.homeText("设置").click()
    val list = device.requireTag("settings-list")
    repeat(2) { swipeList(list, down = true) }
    repeat(2) { swipeList(list, down = false) }
    device.pressBack()
    device.requireTag("home-actions")
    device.waitForIdle(1_000)
}

fun MacrobenchmarkScope.command() {
    device.homeText("应用模式").click()
    val editor = device.requireTag("command-editor")
    editor.click()
    repeat(8) {
        editor.text = "execute as @a at @s run say benchmark_$it"
        device.waitForIdle(1_000)
    }
    // Selection handles, the IME and navigation may each consume Back independently.
    repeat(4) {
        if (!device.hasObject(By.res("home-actions"))) {
            device.pressBack()
            device.waitForIdle(1_000)
        }
    }
    device.requireTag("home-actions")
    device.waitForIdle(1_000)
}

fun MacrobenchmarkScope.library() {
    device.homeText("命令库").click()
    device.waitForIdle(1_000)
    device.findObject(By.text("云端"))?.click()
    device.requireText("性能命令库 1").click()
    val document = device.requireTag("library-document")
    repeat(3) { swipeList(document, down = true) }
    repeat(2) { swipeList(document, down = false) }
    device.pressBack()
    device.waitForIdle(1_000)
}

fun MacrobenchmarkScope.prepareLongCommand() {
    home()
    val command =
        "titleraw @a actionbar {\"rawtext\":[" +
            (1..200).joinToString(",") { "{\"text\":\"benchmark_$it\"}" } +
            "]}"
    device.homeText("设置").click()
    val settings = device.requireTag("settings-list")
    settings.scrollUntil(Direction.DOWN, Until.hasObject(By.res("setting-syntax-highlight")))
    val toggle = device.requireTag("setting-syntax-highlight").findObject(By.checkable(true))
    check(toggle != null && toggle.isChecked) { "Long-command benchmark requires syntax colors" }
    settings.scrollUntil(Direction.DOWN, Until.hasObject(By.res("setting-highlight-limit")))
    val limitRow = device.requireTag("setting-highlight-limit")
    val description =
        limitRow.text.orEmpty() +
            limitRow.findObjects(By.textContains("当前限制:")).joinToString { it.text.orEmpty() }
    val limit = Regex("当前限制: (\\d+) 字符").find(description)?.groupValues?.get(1)?.toIntOrNull()
    check(limit != null && command.length < limit) {
        "Long command exceeds the configured highlighting threshold: $limit"
    }
    device.pressBack()
    device.requireTag("home-actions")
    device.homeText("应用模式").click()
    val editor = device.requireTag("command-editor")
    editor.click()
    editor.text = command
    // Warm the real command engine and wait for its syntax colors before measuring scrolling.
    device.waitForIdle(1_000)
    device.requireTag("command-glyph-cache")
}

fun MacrobenchmarkScope.scrollLongCommand() {
    check(device.hasObject(By.descContains("长按进入多行编辑"))) {
        "Long-command scrolling must measure the single-line editor"
    }
    val editor = device.requireTag("command-editor")
    val bounds = editor.visibleBounds
    val left = bounds.left + bounds.width() / 10
    val right = bounds.right - bounds.width() / 10
    val y = bounds.centerY()
    repeat(3) {
        device.swipe(left, y, right, y, 40)
        device.waitForIdle(1_000)
    }
    repeat(3) {
        device.swipe(right, y, left, y, 40)
        device.waitForIdle(1_000)
    }
}

fun MacrobenchmarkScope.allJourneys() {
    home()
    settings()
    command()
    library()
}

private fun MacrobenchmarkScope.swipeList(list: UiObject2, down: Boolean) {
    val bounds = list.visibleBounds
    val top = bounds.top + bounds.height() / 5
    val bottom = bounds.bottom - bounds.height() / 5
    // Compose may omit AccessibilityEvent.TYPE_VIEW_SCROLLED at a list boundary. UiObject2.fling
    // then waits five seconds per gesture. Inject a real swipe without relying on that event.
    check(
        device.swipe(
            bounds.centerX(),
            if (down) bottom else top,
            bounds.centerX(),
            if (down) top else bottom,
            24,
        )
    )
    device.waitForIdle(1_000)
}

private fun UiDevice.homeText(text: String): UiObject2 {
    repeat(8) {
        val home = requireTag("home-actions")
        home
            .findObject(By.text(text))
            ?.takeIf { !it.visibleBounds.isEmpty }
            ?.let {
                return it
            }
        checkNotNull(home.findObject(By.scrollable(true))) { "Cannot scroll to home action $text" }
            .scroll(Direction.DOWN, 0.7f)
        waitForIdle(1_000)
    }
    error("Missing visible home action $text")
}
