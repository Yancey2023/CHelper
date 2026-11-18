package yancey.chelper.data.local.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import yancey.chelper.domain.library.LibraryFunction

class LocalLibrarySyncResultTest {
    @Test
    fun `匹配上传快照时清除未同步状态`() {
        val result =
            LibraryFunction(content = "> I\nsay old", localIsV2 = true, localUnsynced = true)
                .withLocalSyncResult(
                    "uuid",
                    LibraryFunction(content = "> I\nsay old", localIsV2 = true),
                )

        assertEquals("uuid", result.uuid)
        assertFalse(result.localUnsynced)
    }

    @Test
    fun `旧上传回调不会清除新编辑的未同步状态`() {
        val result =
            LibraryFunction(content = "> I\nsay new", localIsV2 = true, localUnsynced = true)
                .withLocalSyncResult(
                    "uuid",
                    LibraryFunction(content = "> I\nsay old", localIsV2 = true),
                )

        assertEquals("uuid", result.uuid)
        assertTrue(result.localUnsynced)
    }

    @Test
    fun `只修改名称也不会被旧上传回调误清状态`() {
        val result =
            LibraryFunction(
                    name = "new",
                    content = "say hello",
                    localIsV2 = false,
                    localUnsynced = true,
                )
                .withLocalSyncResult(
                    "uuid",
                    LibraryFunction(name = "old", content = "say hello", localIsV2 = false),
                )

        assertTrue(result.localUnsynced)
    }

    @Test
    fun `首次上传期间的新编辑会被标记为未同步`() {
        val result =
            LibraryFunction(content = "say new", localIsV2 = false, localUnsynced = false)
                .withLocalSyncResult(
                    "uuid",
                    LibraryFunction(content = "say old", localIsV2 = false),
                )

        assertEquals("uuid", result.uuid)
        assertTrue(result.localUnsynced)
    }
}
