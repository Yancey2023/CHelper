package yancey.chelper.domain.assets

class Announcement(
    val isEnable: Boolean? = null,
    val isForce: Boolean? = null,
    val isBigDialog: Boolean? = null,
    val isEnableCommandLab: Boolean? = null,
    val publicLibraryMinVersion: Int? = null,
    val title: String? = null,
    val message: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Announcement) return false

        if (isEnable != other.isEnable) return false
        if (isForce != other.isForce) return false
        if (isBigDialog != other.isBigDialog) return false
        if (title != other.title) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isEnable?.hashCode() ?: 0
        result = 31 * result + (isForce?.hashCode() ?: 0)
        result = 31 * result + (isBigDialog?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (message?.hashCode() ?: 0)
        return result
    }
}
