package fr.passerelle.client.feature.files.presentation

import app.cash.turbine.test
import fr.passerelle.client.core.designsystem.error.ErrorMessageKey
import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.model.Breadcrumb
import fr.passerelle.client.core.domain.model.CreateShareParams
import fr.passerelle.client.core.domain.model.DirectoryContent
import fr.passerelle.client.core.domain.model.FileItem
import fr.passerelle.client.core.domain.model.ShareLink
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import fr.passerelle.client.core.domain.model.UpdateShareParams
import fr.passerelle.client.core.domain.repository.FileRepository
import fr.passerelle.client.core.domain.repository.ShareRepository
import fr.passerelle.client.core.domain.usecase.BrowseDirectoryUseCase
import fr.passerelle.client.core.domain.usecase.CreateShareUseCase
import fr.passerelle.client.core.domain.usecase.GetShareQrSvgUseCase
import fr.passerelle.client.core.domain.usecase.GetSharesUseCase
import fr.passerelle.client.core.domain.usecase.SearchFilesUseCase
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

private val SAMPLE_DATE = Instant.parse("2026-11-27T09:00:00Z")

private class FakeFileRepositoryForVm : FileRepository {
    val dir = FileItem("Photos", "Photos", true, null, SAMPLE_DATE, null, false, 1)
    val file = FileItem("plage.jpg", "Photos/plage.jpg", false, 1024L, SAMPLE_DATE, "image/jpeg", true, 0)
    var lastPath: String = ""
    var lastSortBy: SortOption = SortOption.NAME
    var lastSortOrder: SortDirection = SortDirection.ASC
    var lastShowHidden: Boolean = false

    override suspend fun browse(
        path: String,
        sortBy: SortOption,
        sortOrder: SortDirection,
        showHidden: Boolean,
    ): Resource<DirectoryContent> {
        lastPath = path
        lastSortBy = sortBy
        lastSortOrder = sortOrder
        lastShowHidden = showHidden
        return Resource.Success(
            DirectoryContent(
                currentPath = path,
                parentPath = null,
                breadcrumbs = listOf(Breadcrumb("Racine", "")),
                entries = listOf(dir, file),
            )
        )
    }

    override suspend fun search(query: String, path: String, showHidden: Boolean): Resource<List<FileItem>> =
        Resource.Success(listOf(file).filter { it.name.contains(query, ignoreCase = true) })

    override suspend fun getThumbnail(path: String): Resource<ByteArray> =
        Resource.Success(byteArrayOf(7, 8, 9))

    override suspend fun getDirectDownloadUrl(path: String, inline: Boolean): String =
        "https://s.domaine.fr/api/v1/files/download?path=$path"
}

private class FakeShareRepositoryForFilesVm : ShareRepository {
    val shares = mutableListOf<ShareLink>()
    var nextError: AppError? = null

    override suspend fun createShare(params: CreateShareParams): Resource<ShareLink> {
        nextError?.let { return Resource.Error(it) }
        val link = ShareLink(
            id = "s-${shares.size + 1}",
            slug = params.customSlug ?: "rand1234",
            publicUrl = "https://s.domaine.fr/${params.customSlug ?: "rand1234"}",
            directDownloadUrl = "https://s.domaine.fr/d/${params.customSlug ?: "rand1234"}",
            targetName = params.relativePath.substringAfterLast('/'),
            relativePath = params.relativePath,
            isDirectory = false,
            hasPassword = !params.password.isNullOrEmpty(),
            expiresAt = null,
            maxDownloads = params.maxDownloads,
            downloadCount = 0,
            isRevoked = false,
            isActive = true,
            createdAt = SAMPLE_DATE,
            lastAccessedAt = null,
        )
        shares.add(link)
        return Resource.Success(link)
    }

    override suspend fun getShares(path: String?, activeOnly: Boolean): Resource<List<ShareLink>> =
        Resource.Success(shares.filter { path == null || it.relativePath == path })

    override suspend fun updateShare(params: UpdateShareParams): Resource<ShareLink> =
        Resource.Error(AppError.NotFound)

    override suspend fun deleteShare(id: String): Resource<Unit> = Resource.Success(Unit)

    override suspend fun getShareQrSvg(id: String): Resource<String> =
        Resource.Success("<svg viewBox=\"0 0 21 21\"><rect x=\"0\" y=\"0\" width=\"1\" height=\"1\"/></svg>")
}

private class FakePlatformActionsForFilesVm : PlatformActions {
    override val deviceName: String = "Test"
    var copiedText: String? = null
    var sharedUrl: String? = null
    var shareOutcome: ShareOutcome = ShareOutcome.COPIED_TO_CLIPBOARD

    override fun copyToClipboard(text: String) { copiedText = text }
    override suspend fun shareLink(url: String, title: String): ShareOutcome {
        sharedUrl = url
        return shareOutcome
    }
    override fun openExternalUrl(url: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {

    private val fileRepo = FakeFileRepositoryForVm()
    private val shareRepo = FakeShareRepositoryForFilesVm()
    private val platform = FakePlatformActionsForFilesVm()
    private lateinit var viewModel: FilesViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = FilesViewModel(
            browseDirectoryUseCase = BrowseDirectoryUseCase(fileRepo),
            searchFilesUseCase = SearchFilesUseCase(fileRepo),
            createShareUseCase = CreateShareUseCase(shareRepo),
            getSharesUseCase = GetSharesUseCase(shareRepo),
            getShareQrSvgUseCase = GetShareQrSvgUseCase(shareRepo),
            fileRepository = fileRepo,
            platformActions = platform,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load populates entries and thumbnails`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(2, state.entries.size)
        assertTrue(state.thumbnails.containsKey("Photos/plage.jpg"))
    }

    @Test
    fun `sort toggles direction when selecting same option and changes option otherwise`() = runTest {
        viewModel.onSortSelected(SortOption.NAME)
        assertEquals(SortDirection.DESC, viewModel.uiState.value.sortDirection)

        viewModel.onSortSelected(SortOption.SIZE)
        assertEquals(SortOption.SIZE, viewModel.uiState.value.sortOption)
        assertEquals(SortDirection.ASC, viewModel.uiState.value.sortDirection)
    }

    @Test
    fun `search switches to search results and clear restores directory`() = runTest {
        viewModel.onSearchQueryChanged("plage")
        assertTrue(viewModel.uiState.value.isSearchActive)
        assertEquals(1, viewModel.uiState.value.entries.size)

        viewModel.onClearSearch()
        assertFalse(viewModel.uiState.value.isSearchActive)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(2, viewModel.uiState.value.entries.size)
    }

    @Test
    fun `createShare validates slug locally and creates share on valid input`() = runTest {
        viewModel.onInspectItem(fileRepo.file)
        viewModel.onCustomSlugChanged("ab")
        viewModel.createShare()
        assertEquals(ErrorMessageKey.INVALID_SLUG_FORMAT, viewModel.uiState.value.createShareForm.error?.key)

        viewModel.onCustomSlugChanged("plage-ete")
        viewModel.onQuotaPresetChanged(QuotaPreset.FIVE)
        viewModel.onExpiryPresetChanged(ExpiryPreset.ONE_DAY)

        viewModel.events.test {
            viewModel.createShare()
            val created = viewModel.uiState.value.lastCreatedShare
            assertNotNull(created)
            assertEquals("plage-ete", created.slug)
            assertEquals(1, viewModel.uiState.value.selectedItemShares.size)
            assertNull(viewModel.uiState.value.createShareForm.error)
            assertEquals(FilesEvent.ShowMessage(FilesEvent.ShowMessage.MessageType.SHARE_CREATED), awaitItem())
        }
    }

    @Test
    fun `createQuickShare triggers native share or copies with snackbar`() = runTest {
        platform.shareOutcome = ShareOutcome.COPIED_TO_CLIPBOARD
        viewModel.events.test {
            viewModel.createQuickShareAndTriggerNativeShare(fileRepo.file)
            assertNotNull(platform.sharedUrl)
            assertEquals(FilesEvent.ShowMessage(FilesEvent.ShowMessage.MessageType.LINK_COPIED), awaitItem())
        }
    }

    @Test
    fun `natural load delays pull-to-refresh spinner by 300ms and hides it on completion`() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val slowRepo = SlowFileRepositoryForVm(delayMs = 500L)
        val vm = FilesViewModel(
            browseDirectoryUseCase = BrowseDirectoryUseCase(slowRepo),
            searchFilesUseCase = SearchFilesUseCase(slowRepo),
            createShareUseCase = CreateShareUseCase(shareRepo),
            getSharesUseCase = GetSharesUseCase(shareRepo),
            getShareQrSvgUseCase = GetShareQrSvgUseCase(shareRepo),
            fileRepository = slowRepo,
            platformActions = platform,
        )

        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)

        testScheduler.advanceTimeBy(299L)
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)

        testScheduler.advanceTimeBy(1L)
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isPullToRefreshVisible)

        testScheduler.advanceTimeBy(200L)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)
    }

    @Test
    fun `fast natural load under 300ms never shows pull-to-refresh spinner`() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val fastRepo = SlowFileRepositoryForVm(delayMs = 150L)
        val vm = FilesViewModel(
            browseDirectoryUseCase = BrowseDirectoryUseCase(fastRepo),
            searchFilesUseCase = SearchFilesUseCase(fastRepo),
            createShareUseCase = CreateShareUseCase(shareRepo),
            getSharesUseCase = GetSharesUseCase(shareRepo),
            getShareQrSvgUseCase = GetShareQrSvgUseCase(shareRepo),
            fileRepository = fastRepo,
            platformActions = platform,
        )

        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)

        testScheduler.advanceTimeBy(150L)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)
    }

    @Test
    fun `pull-to-refresh shows spinner immediately without 300ms delay`() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val slowRepo = SlowFileRepositoryForVm(delayMs = 500L)
        val vm = FilesViewModel(
            browseDirectoryUseCase = BrowseDirectoryUseCase(slowRepo),
            searchFilesUseCase = SearchFilesUseCase(slowRepo),
            createShareUseCase = CreateShareUseCase(shareRepo),
            getSharesUseCase = GetSharesUseCase(shareRepo),
            getShareQrSvgUseCase = GetShareQrSvgUseCase(shareRepo),
            fileRepository = slowRepo,
            platformActions = platform,
        )

        // Finish initial load
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)

        // Trigger pull-to-refresh
        vm.refresh(fromPullToRefresh = true)
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isPullToRefreshVisible)

        // Finish pull-to-refresh
        testScheduler.advanceTimeBy(500L)
        testScheduler.runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)
    }

    @Test
    fun `pull-to-refresh during active search shows spinner immediately`() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val slowRepo = SlowFileRepositoryForVm(delayMs = 500L)
        val vm = FilesViewModel(
            browseDirectoryUseCase = BrowseDirectoryUseCase(slowRepo),
            searchFilesUseCase = SearchFilesUseCase(slowRepo),
            createShareUseCase = CreateShareUseCase(shareRepo),
            getSharesUseCase = GetSharesUseCase(shareRepo),
            getShareQrSvgUseCase = GetShareQrSvgUseCase(shareRepo),
            fileRepository = slowRepo,
            platformActions = platform,
        )

        testScheduler.advanceUntilIdle()

        // Natural search: spinner delayed by 300ms
        vm.onSearchQueryChanged("plage")
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)

        testScheduler.advanceTimeBy(300L)
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isPullToRefreshVisible)

        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isPullToRefreshVisible)

        // Pull-to-refresh during search: spinner immediate
        vm.refresh(fromPullToRefresh = true)
        testScheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isPullToRefreshVisible)

        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isPullToRefreshVisible)
    }
}

private class SlowFileRepositoryForVm(
    private val delayMs: Long,
) : FileRepository {
    val file = FileItem("plage.jpg", "Photos/plage.jpg", false, 1024L, SAMPLE_DATE, "image/jpeg", false, 0)

    override suspend fun browse(
        path: String,
        sortBy: SortOption,
        sortOrder: SortDirection,
        showHidden: Boolean,
    ): Resource<DirectoryContent> {
        kotlinx.coroutines.delay(delayMs)
        return Resource.Success(
            DirectoryContent(
                currentPath = path,
                parentPath = null,
                breadcrumbs = listOf(Breadcrumb("Racine", "")),
                entries = listOf(file),
            )
        )
    }

    override suspend fun search(query: String, path: String, showHidden: Boolean): Resource<List<FileItem>> {
        kotlinx.coroutines.delay(delayMs)
        return Resource.Success(listOf(file))
    }

    override suspend fun getThumbnail(path: String): Resource<ByteArray> =
        Resource.Success(byteArrayOf(7, 8, 9))

    override suspend fun getDirectDownloadUrl(path: String, inline: Boolean): String =
        "https://s.domaine.fr/api/v1/files/download?path=$path"
}
