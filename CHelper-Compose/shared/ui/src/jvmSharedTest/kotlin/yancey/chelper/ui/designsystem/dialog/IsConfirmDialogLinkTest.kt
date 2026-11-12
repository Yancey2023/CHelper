package yancey.chelper.ui.designsystem.dialog

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

class IsConfirmDialogLinkTest {
    @Test
    fun `公告正文中的多个链接会被标记`() {
        val text = "官网：https://example.com/docs，下载 http://127.0.0.1:8080/app.apk。"

        val result = annotateHttpLinks(text, Color.Blue)
        val links =
            result.getLinkAnnotations(0, result.length).map { (it.item as LinkAnnotation.Url).url }

        assertEquals(text, result.text)
        assertEquals(listOf("https://example.com/docs", "http://127.0.0.1:8080/app.apk"), links)
    }

    @Test
    fun `链接后的英文标点不会被带入地址`() {
        val text = "Open (https://example.com/download), then continue."

        val result = annotateHttpLinks(text, Color.Blue)
        val links =
            result.getLinkAnnotations(0, result.length).map { (it.item as LinkAnnotation.Url).url }

        assertEquals(text, result.text)
        assertEquals(listOf("https://example.com/download"), links)
    }

    @Test
    fun `自定义点击回调会收到原始链接`() {
        var clickedUrl: String? = null
        val result =
            annotateHttpLinks("https://abyssous.site/invite?code=test", Color.Blue) {
                clickedUrl = it
            }

        val link = result.getLinkAnnotations(0, result.length).single().item
        link.linkInteractionListener?.onClick(link)

        assertEquals("https://abyssous.site/invite?code=test", clickedUrl)
    }
}
