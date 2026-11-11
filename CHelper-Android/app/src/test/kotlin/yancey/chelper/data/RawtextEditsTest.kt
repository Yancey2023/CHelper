package yancey.chelper.data

import org.junit.Assert.*
import org.junit.Test

class RawtextEditsTest {
    @Test
    fun `merging unrelated edits preserves the current draft and settings`() {
        val edits = RawtextEditTracker()
        val draft = edits.claim(RawtextField.DRAFT)
        val format = edits.claim(RawtextField.COPY_FORMAT)
        val current =
            RawtextPreferences(
                autoSaveEnabled = true,
                copyFormat = "formatted",
                draft = "old",
                mockSettings = "current mock",
            )
        val updated = edits.merge(current, current.copy(draft = "latest"), draft)
        assertEquals(
            updated.copy(copyFormat = "compressed"),
            edits.merge(updated, RawtextPreferences(copyFormat = "compressed"), format),
        )
    }

    @Test
    fun `disabled autosave cannot be reenabled by another editors stale snapshot`() {
        val edits = RawtextEditTracker()
        val before = RawtextPreferences(autoSaveEnabled = true, draft = "old")
        val oldDraft = edits.claim(RawtextField.DRAFT)
        val disable = edits.claim(RawtextField.AUTO_SAVE, RawtextField.DRAFT)
        val disabled = edits.merge(before, before.copy(autoSaveEnabled = false), disable)
        assertFalse(disabled.autoSaveEnabled)
        assertNull(disabled.draft)
        assertEquals(disabled, edits.merge(disabled, before.copy(draft = "obsolete"), oldDraft))
        val typing = edits.claim(RawtextField.DRAFT)
        assertEquals(
            disabled,
            edits.merge(disabled, before.copy(draft = "typed while disabled elsewhere"), typing),
        )
    }
}
