package fr.passerelle.client.core.data.mapper

import fr.passerelle.client.core.data.remote.dto.AuthSessionDto
import fr.passerelle.client.core.data.remote.dto.BreadcrumbDto
import fr.passerelle.client.core.data.remote.dto.CreateShareRequestDto
import fr.passerelle.client.core.data.remote.dto.DirectoryContentDto
import fr.passerelle.client.core.data.remote.dto.FileItemDto
import fr.passerelle.client.core.data.remote.dto.ShareDto
import fr.passerelle.client.core.data.remote.dto.UpdateShareRequestDto
import fr.passerelle.client.core.domain.model.AuthSession
import fr.passerelle.client.core.domain.model.Breadcrumb
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.DirectoryContent
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.UpdateShareParams
import kotlinx.datetime.Instant

internal fun AuthSessionDto.toDomain(): AuthSession = AuthSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresInSeconds = expiresInSeconds,
    publicBaseUrl = publicBaseUrl,
)

internal fun BreadcrumbDto.toDomain(): Breadcrumb = Breadcrumb(
    name = name,
    path = path,
)

internal fun FileItemDto.toDomain(): FileItem = FileItem(
    name = name,
    path = path,
    isDirectory = isDirectory,
    sizeBytes = sizeBytes,
    modifiedAt = Instant.parse(modifiedAt),
    mimeType = mimeType,
    hasThumbnail = hasThumbnail,
    activeShareCount = activeShareCount,
)

internal fun DirectoryContentDto.toDomain(): DirectoryContent = DirectoryContent(
    currentPath = currentPath,
    parentPath = parentPath,
    breadcrumbs = breadcrumbs.map { it.toDomain() },
    entries = entries.map { it.toDomain() },
)

internal fun ShareDto.toDomain(): ShareLink = ShareLink(
    id = id,
    slug = slug,
    publicUrl = publicUrl,
    directDownloadUrl = directDownloadUrl,
    targetName = targetName,
    relativePath = relativePath,
    isDirectory = isDirectory,
    hasPassword = hasPassword,
    expiresAt = expiresAt?.let(Instant::parse),
    maxDownloads = maxDownloads,
    downloadCount = downloadCount,
    isRevoked = isRevoked,
    isActive = isActive,
    createdAt = Instant.parse(createdAt),
    lastAccessedAt = lastAccessedAt?.let(Instant::parse),
)

internal fun CreateShareParams.toDto(): CreateShareRequestDto = CreateShareRequestDto(
    relativePath = relativePath,
    customSlug = customSlug,
    password = password,
    expiresInSeconds = expiresInSeconds,
    maxDownloads = maxDownloads,
)

internal fun UpdateShareParams.toDto(): UpdateShareRequestDto = UpdateShareRequestDto(
    isRevoked = isRevoked,
    expiresAt = expiresAt?.toString(),
    clearExpiration = clearExpiration,
    maxDownloads = maxDownloads,
    clearMaxDownloads = clearMaxDownloads,
    newPassword = newPassword,
    clearPassword = clearPassword,
)
