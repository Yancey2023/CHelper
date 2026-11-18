package yancey.chelper.domain.library

enum class LocalLibrarySort {
    DEFAULT,
    NEWEST,
    OLDEST,
    NAME_ASC,
    NAME_DESC,
}

data class LocalLibraryEntry(val storageIndex: Int, val library: LibraryFunction) {
    val localEntryId: String
        get() = requireNotNull(library.localEntryId)
}
