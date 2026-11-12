package yancey.chelper.ui.feature.settings

/** Same labels and branch values in the Android and desktop settings pages. */
fun resourcePackOptions(filenames: List<String>): Array<Pair<String, String>> {
    val branches =
        listOf(
            "release-vanilla" to "正式版-原版",
            "release-experiment" to "正式版-实验性玩法",
            "beta-vanilla" to "测试版-原版",
            "beta-experiment" to "测试版-实验性玩法",
            "netease-vanilla" to "中国版-原版",
            "netease-experiment" to "中国版-实验性玩法",
        )
    return filenames
        .mapNotNull { filename ->
            val (id, name) =
                branches.firstOrNull { filename.startsWith(it.first + "-") }
                    ?: return@mapNotNull null
            "$name-${filename.removePrefix("$id-").removeSuffix(".cpack")}" to id
        }
        .toTypedArray()
}
