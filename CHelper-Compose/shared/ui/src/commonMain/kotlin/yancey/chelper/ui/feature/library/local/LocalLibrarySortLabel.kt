package yancey.chelper.ui.feature.library.local

import yancey.chelper.domain.library.LocalLibrarySort

val LocalLibrarySort.label: String
    get() =
        when (this) {
            LocalLibrarySort.DEFAULT -> "默认顺序"
            LocalLibrarySort.NEWEST -> "最近添加"
            LocalLibrarySort.OLDEST -> "最早添加"
            LocalLibrarySort.NAME_ASC -> "名称 A-Z"
            LocalLibrarySort.NAME_DESC -> "名称 Z-A"
        }
