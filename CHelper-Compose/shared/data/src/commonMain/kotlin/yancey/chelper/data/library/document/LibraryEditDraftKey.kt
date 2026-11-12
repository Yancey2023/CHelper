package yancey.chelper.data.library.document

/** Persisted editor keys are shared by the original UI, the repositories and legacy migrations. */
fun localLibraryEditDraftKey(localEntryId: String? = null, index: Int? = null): String =
    localEntryId?.let { "local-library:update:$it" }
        ?: index?.let { "local-library:update:index:$it" }
        ?: "local-library:add"
