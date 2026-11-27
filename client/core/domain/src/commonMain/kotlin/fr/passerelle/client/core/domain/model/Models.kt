package fr.passerelle.client.core.domain.model

import kotlinx.datetime.Instant

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val publicBaseUrl: String,
)

enum class SortOption(val apiValue: String) {
    NAME("name"),
    SIZE("size"),
    MODIFIED_AT("modifiedAt"),
}

enum class SortDirection(val apiValue: String) {
    ASC("asc"),
    DESC("desc"),
}

data class Breadcrumb(
    val name: String,
    val path: String,
)

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedAt: Instant,
    val mimeType: String?,
    val hasThumbnail: Boolean,
    val activeShareCount: Int,
)

data class DirectoryContent(
    val currentPath: String,
    val parentPath: String?,
    val breadcrumbs: List<Breadcrumb>,
    val entries: List<FileItem>,
)

data class ShareLink(
    val id: String,
    val slug: String,
    val publicUrl: String,
    val directDownloadUrl: String,
    val targetName: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val hasPassword: Boolean,
    val expiresAt: Instant?,
    val maxDownloads: Int?,
    val downloadCount: Int,
    val isRevoked: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val lastAccessedAt: Instant?,
)

data class CreateShareParams(
    val relativePath: String,
    val customSlug: String? = null,
    val password: String? = null,
    val expiresInSeconds: Long? = null,
    val maxDownloads: Int? = null,
)

data class UpdateShareParams(
    val id: String,
    val isRevoked: Boolean? = null,
    val expiresAt: Instant? = null,
    val clearExpiration: Boolean = false,
    val maxDownloads: Int? = null,
    val clearMaxDownloads: Boolean = false,
    val newPassword: String? = null,
    val clearPassword: Boolean = false,
)
