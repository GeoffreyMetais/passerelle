package fr.passerelle.client.feature.shares.presentation

import app.cash.turbine.test
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.UpdateShareParams
import fr.passerelle.client.core.domain.repository.ShareRepository
import fr.passerelle.client.core.domain.usecase.DeleteShareUseCase
import fr.passerelle.client.core.domain.usecase.GetShareQrSvgUseCase
import fr.passerelle.client.core.domain.usecase.GetSharesUseCase
import fr.passerelle.client.core.domain.usecase.UpdateShareUseCase
import fr.passerelle.client.core.platform.PlatformActions
import fr.passerelle.client.core.platform.ShareOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NOW = Instant.parse("2026-11-27T09:00:00Z")

private class FakeShareRepositoryForSharesVm : ShareRepository {
    val items = mutableListOf(
        ShareLink(
            id = "s-1",
            slug = "vacances",
            publicUrl = "https://s.domaine.fr/vacances",
            directDownloadUrl = "https://s.domaine.fr/d/vacances",
            targetName = "Vacances",
            relativePath = "Photos/Vacances",
            isDirectory = true,
            hasPassword = true,
            expiresAt = null,
            maxDownloads = 10,
            downloadCount = 2,
            isRevoked = false,
            isActive = true,
            createdAt = NOW,
            lastAccessedAt = null,
        ),
        ShareLink(
            id = "s-2",
            slug = "ancien",
            publicUrl = "https://s.domaine.fr/ancien",
            directDownloadUrl = "https://s.domaine.fr/d/ancien",
            targetName = "notes.txt",
            relativePath = "Docs/notes.txt",
            isDirectory = false,
            hasPassword = false,
            expiresAt = null,
            maxDownloads = 1,
            downloadCount = 1,
            isRevoked = true,
            isActive = false,
            createdAt = NOW,
            lastAccessedAt = NOW,
        ),
    )

    override suspend fun createShare(params: CreateShareParams): Resource<ShareLink> =
        Resource.Error(AppError.UnexpectedError())

    override suspend fun getShares(path: String?, activeOnly: Boolean): Resource<List<ShareLink>> =
        Resource.Success(items.toList())

    override suspend fun updateShare(params: UpdateShareParams): Resource<ShareLink> {
        val index = items.indexOfFirst { it.id == params.id }
        if (index < 0) return Resource.Error(AppError.NotFound)
        val old = items[index]
        val updated = old.copy(
            isRevoked = params.isRevoked ?: old.isRevoked,
            isActive = !(params.isRevoked ?: old.isRevoked),
            maxDownloads = if (params.clearMaxDownloads) null else params.maxDownloads ?: old.maxDownloads,
        )
        items[index] = updated
        return Resource.Success(updated)
    }

    override suspend fun deleteShare(id: String): Resource<Unit> {
        items.removeAll { it.id == id }
        return Resource.Success(Unit)
    }

    override suspend fun getShareQrSvg(id: String): Resource<String> =
        Resource.Success("<svg viewBox=\"0 0 21 21\"><rect x=\"0\" y=\"0\" width=\"1\" height=\"1\"/></svg>")
}

private class FakePlatformActionsForSharesVm : PlatformActions {
    override val deviceName: String = "Test"
    var copied: String? = null
    override fun copyToClipboard(text: String) { copied = text }
    override suspend fun shareLink(url: String, title: String): ShareOutcome = ShareOutcome.OPENED_NATIVE_SHEET
    override fun openExternalUrl(url: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharesViewModelTest {

    private val shareRepo = FakeShareRepositoryForSharesVm()
    private val platform = FakePlatformActionsForSharesVm()
    private lateinit var viewModel: SharesViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SharesViewModel(
            getSharesUseCase = GetSharesUseCase(shareRepo),
            updateShareUseCase = UpdateShareUseCase(shareRepo),
            deleteShareUseCase = DeleteShareUseCase(shareRepo),
            getShareQrSvgUseCase = GetShareQrSvgUseCase(shareRepo),
            platformActions = platform,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates shares and filter chips filter correctly`() = runTest {
        assertEquals(2, viewModel.uiState.value.filteredShares.size)

        viewModel.onFilterChanged(ShareFilter.ACTIVE)
        assertEquals(1, viewModel.uiState.value.filteredShares.size)
        assertEquals("vacances", viewModel.uiState.value.filteredShares.first().slug)

        viewModel.onFilterChanged(ShareFilter.INACTIVE)
        assertEquals(1, viewModel.uiState.value.filteredShares.size)
        assertEquals("ancien", viewModel.uiState.value.filteredShares.first().slug)

        viewModel.onFilterChanged(ShareFilter.ALL)
        viewModel.onSearchQueryChanged("Docs")
        assertEquals(1, viewModel.uiState.value.filteredShares.size)
    }

    @Test
    fun `edit share revokes link and updates list`() = runTest {
        val target = viewModel.uiState.value.allShares.first()
        viewModel.openEditDialog(target)
        assertNotNull(viewModel.uiState.value.editingShare)
        viewModel.onEditRevokedChanged(true)

        viewModel.events.test {
            viewModel.submitEdit()
            assertNull(viewModel.uiState.value.editingShare)
            assertFalse(viewModel.uiState.value.allShares.first { it.id == target.id }.isActive)
            assertEquals(SharesEvent.ShowMessage(SharesEvent.ShowMessage.MessageType.SHARE_UPDATED), awaitItem())
        }
    }

    @Test
    fun `delete share removes link from list`() = runTest {
        val target = viewModel.uiState.value.allShares.first()
        viewModel.requestDelete(target)
        assertEquals(target.id, viewModel.uiState.value.deletingShare?.id)

        viewModel.events.test {
            viewModel.confirmDelete()
            assertNull(viewModel.uiState.value.deletingShare)
            assertEquals(1, viewModel.uiState.value.allShares.size)
            assertEquals(SharesEvent.ShowMessage(SharesEvent.ShowMessage.MessageType.SHARE_DELETED), awaitItem())
        }
    }

    @Test
    fun `openQrModal loads SVG and copyLink copies public url`() = runTest {
        val target = viewModel.uiState.value.allShares.first()
        viewModel.openQrModal(target)
        assertTrue(viewModel.uiState.value.qrSvgForModal?.contains("<svg") == true)
        viewModel.dismissQrModal()
        assertNull(viewModel.uiState.value.qrSvgForModal)

        viewModel.events.test {
            viewModel.copyLink(target)
            assertEquals(target.publicUrl, platform.copied)
            assertEquals(SharesEvent.ShowMessage(SharesEvent.ShowMessage.MessageType.LINK_COPIED), awaitItem())
        }
    }
}
