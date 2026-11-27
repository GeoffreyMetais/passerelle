package fr.passerelle.client.core.domain.repository

import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.model.AuthSession
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.DirectoryContent
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.core.domain.model.UpdateShareParams
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeIsAuthenticated(): Flow<Boolean>
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        deviceName: String,
    ): Resource<AuthSession>

    suspend fun logout(): Resource<Unit>
}

interface FileRepository {
    suspend fun browse(
        path: String = "",
        sortBy: SortOption = SortOption.NAME,
        sortOrder: SortDirection = SortDirection.ASC,
        showHidden: Boolean = false,
    ): Resource<DirectoryContent>

    suspend fun search(
        query: String,
        path: String = "",
        showHidden: Boolean = false,
    ): Resource<List<FileItem>>

    suspend fun getThumbnail(path: String): Resource<ByteArray>

    suspend fun getDirectDownloadUrl(path: String, inline: Boolean = false): String
}

interface ShareRepository {
    suspend fun createShare(params: CreateShareParams): Resource<ShareLink>

    suspend fun getShares(
        path: String? = null,
        activeOnly: Boolean = false,
    ): Resource<List<ShareLink>>

    suspend fun updateShare(params: UpdateShareParams): Resource<ShareLink>

    suspend fun deleteShare(id: String): Resource<Unit>

    suspend fun getShareQrSvg(id: String): Resource<String>
}
