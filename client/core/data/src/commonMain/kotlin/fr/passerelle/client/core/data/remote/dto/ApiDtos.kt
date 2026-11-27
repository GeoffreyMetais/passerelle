package fr.passerelle.client.core.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class ApiErrorDto(
    val code: String,
    val message: String,
)

@Serializable
internal data class LoginRequestDto(
    val username: String,
    val password: String,
    val deviceName: String,
)

@Serializable
internal data class RefreshRequestDto(
    val refreshToken: String,
)

@Serializable
internal data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val publicBaseUrl: String,
)

@Serializable
internal data class BreadcrumbDto(
    val name: String,
    val path: String,
)

@Serializable
internal data class FileItemDto(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val modifiedAt: String,
    val mimeType: String? = null,
    val hasThumbnail: Boolean,
    val activeShareCount: Int,
)

@Serializable
internal data class DirectoryContentDto(
    val currentPath: String,
    val parentPath: String? = null,
    val breadcrumbs: List<BreadcrumbDto>,
    val entries: List<FileItemDto>,
)

@Serializable
internal data class SearchResponseDto(
    val query: String,
    val results: List<FileItemDto>,
)

@Serializable
internal data class ShareDto(
    val id: String,
    val slug: String,
    val publicUrl: String,
    val directDownloadUrl: String,
    val targetName: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val hasPassword: Boolean,
    val expiresAt: String? = null,
    val maxDownloads: Int? = null,
    val downloadCount: Int,
    val isRevoked: Boolean,
    val isActive: Boolean,
    val createdAt: String,
    val lastAccessedAt: String? = null,
)

@Serializable
internal data class SharesResponseDto(
    val shares: List<ShareDto>,
)

@Serializable
internal data class CreateShareRequestDto(
    val relativePath: String,
    val customSlug: String? = null,
    val password: String? = null,
    val expiresInSeconds: Long? = null,
    val maxDownloads: Int? = null,
)

@Serializable
internal data class UpdateShareRequestDto(
    val isRevoked: Boolean? = null,
    val expiresAt: String? = null,
    val clearExpiration: Boolean = false,
    val maxDownloads: Int? = null,
    val clearMaxDownloads: Boolean = false,
    val newPassword: String? = null,
    val clearPassword: Boolean = false,
)
