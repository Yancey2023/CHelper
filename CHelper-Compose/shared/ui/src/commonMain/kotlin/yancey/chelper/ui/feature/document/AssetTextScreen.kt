package yancey.chelper.ui.feature.document

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.IOException
import yancey.chelper.ui.platform.LocalClientServices as LocalContext

/** Routes carry the asset identifier; document text is loaded off the UI thread. */
@Composable
fun AssetTextScreen(title: String, assetPath: String) {
    val context = LocalContext.current
    val content by
        produceState("加载中...", context, assetPath) {
            value =
                withContext(Dispatchers.IO) {
                    try {
                        context.assets.readText(assetPath)
                    } catch (_: IOException) {
                        "文档加载失败"
                    }
                }
        }
    ShowTextScreen(title, content)
}
