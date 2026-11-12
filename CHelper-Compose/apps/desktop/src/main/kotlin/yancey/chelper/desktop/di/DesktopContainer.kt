package yancey.chelper.desktop.di

import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yancey.chelper.core.CHelperCore
import yancey.chelper.data.local.ClientDataStores
import yancey.chelper.desktop.instance.desktopDataDirectory
import yancey.chelper.desktop.network.DesktopWafBrowser
import yancey.chelper.desktop.platform.DesktopClientPlatform
import yancey.chelper.desktop.storage.DesktopAppearance
import yancey.chelper.desktop.storage.DesktopCredentialStore
import yancey.chelper.desktop.storage.DesktopFiles
import yancey.chelper.desktop.storage.DesktopLegacyMigration
import yancey.chelper.desktop.storage.atomicWrite
import yancey.chelper.desktop.sync.DesktopLibrarySync
import yancey.chelper.domain.assets.AssetRepository
import yancey.chelper.nativecore.loader.NativeLibrary
import yancey.chelper.ui.app.AppDependencies

/** Windows composition root. Implementations are wired here and never looked up by the UI. */
class DesktopContainer(directory: File = desktopDataDirectory()) : Closeable {
    private val directory = directory
    private val files = DesktopFiles(directory)
    private val legacy = DesktopLegacyMigration(directory)
    internal val stores = ClientDataStores(directory)
    val appearance = DesktopAppearance(directory)
    private val packNames by lazy {
        files.text("cpack/index.txt").lineSequence().filter(String::isNotBlank).toList()
    }
    private var nativeFailure: Throwable? = null
    private val sync = DesktopLibrarySync(stores.libraries)
    private val browserVerification =
        DesktopWafBrowser(
            File(directory, "cache/browser-verification"),
            yancey.chelper.data.remote.ServiceManager.LAB_BASE_URL,
        )
    private val platform = DesktopClientPlatform(sync::enqueue)
    internal val loongFlow
        get() = platform.loongFlow

    val dependencies =
        AppDependencies(
            yancey.chelper.ui.platform.ClientServices(
                settings = stores.settings,
                libraries = stores.libraries,
                favorites = stores.favorites,
                history = stores.history,
                platform = platform,
                packFilenames = packNames,
                openCore = { path ->
                    checkNative()
                    CHelperCore.fromFile(files.extract(path).absolutePath)
                },
                commandDrafts = stores.commandDrafts,
                rawtext = stores.rawtext,
                assets =
                    AssetRepository { path -> withContext(Dispatchers.IO) { files.text(path) } },
                versionName = "26.2.0",
                versionCode = 87,
                dataDirectory = directory,
            ),
            convert = { command ->
                withContext(Dispatchers.Default) {
                    checkNative()
                    CHelperCore.old2new(
                        null,
                        files.extract("old2new/old2new.dat").absolutePath,
                        command,
                    )
                }
            },
        )

    init {
        dependencies.services.session.register(appearance, appearance::awaitSaved)
    }

    suspend fun initialize() =
        withContext(Dispatchers.IO) {
            var issue: String? = null
            try {
                legacy.ensure()
            } catch (e: Exception) {
                issue = "旧工作台草稿迁移失败，原文件已保留：${e.message}"
            }
            try {
                NativeLibrary.load(files.nativeLibrary())
            } catch (e: Exception) {
                nativeFailure = e
            } catch (e: LinkageError) {
                nativeFailure = e
            }
            try {
                yancey.chelper.data.remote.ServiceManager.init(File(directory, "cache"), false)
                browserVerification.install()
                var authError: Throwable? = null
                yancey.chelper.data.auth.LoginUtil.init(
                    DesktopCredentialStore(File(directory, "session.enc"))
                ) {
                    authError = it
                }
                authError?.let { issue = "登录状态恢复失败：${it.message}" }
                val device = File(directory, "installation-id")
                if (!device.exists())
                    atomicWrite(device, java.util.UUID.randomUUID().toString().encodeToByteArray())
                yancey.chelper.data.auth.GuestAuthUtil.init(device.readText())
                yancey.chelper.data.policy.PolicyGrantManager.init(
                    files.text("about/privacy_policy.txt"),
                    File(directory, "privacy_policy_lastread.txt"),
                )
                sync.start()
            } catch (e: Exception) {
                issue = e.message
            }
            issue
        }

    private fun checkNative() {
        nativeFailure?.let { throw IllegalStateException("内核加载失败：${it.message}", it) }
    }

    /** Called after the window's ViewModelStore is cleared. */
    override fun close() {
        sync.close()
        browserVerification.close()
        appearance.close()
        stores.close()
    }
}
