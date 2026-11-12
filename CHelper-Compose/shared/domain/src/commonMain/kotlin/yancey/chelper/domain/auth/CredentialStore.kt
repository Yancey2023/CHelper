package yancey.chelper.domain.auth

interface CredentialStore {
    fun read(): String?

    fun write(content: String)

    fun clear()
}
