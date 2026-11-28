package fr.passerelle.client.core.domain.usecase

import fr.passerelle.client.core.domain.error.AppError
import fr.passerelle.client.core.domain.error.Resource
import fr.passerelle.client.core.domain.fake.FakeFileRepository
import fr.passerelle.client.core.domain.model.SortDirection
import fr.passerelle.client.core.domain.model.SortOption
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileUseCasesTest {

    private val fileRepository = FakeFileRepository()
    private val browseDirectoryUseCase = BrowseDirectoryUseCase(fileRepository)
    private val searchFilesUseCase = SearchFilesUseCase(fileRepository)

    @Test
    fun `browse delegates normalized path and options`() = runTest {
        val result = browseDirectoryUseCase(
            path = "/Photos/Vacances/",
            sortBy = SortOption.SIZE,
            sortOrder = SortDirection.DESC,
            showHidden = true,
        )

        assertIs<Resource.Success<*>>(result)
        assertEquals("Photos/Vacances", fileRepository.lastBrowseQuery?.path)
        assertEquals(SortOption.SIZE, fileRepository.lastBrowseQuery?.sortBy)
        assertEquals(SortDirection.DESC, fileRepository.lastBrowseQuery?.sortOrder)
        assertTrue(fileRepository.lastBrowseQuery?.showHidden == true)
    }

    @Test
    fun `search rejects blank query before calling repository`() = runTest {
        val result = searchFilesUseCase(query = "   ")

        val error = assertIs<Resource.Error>(result)
        assertIs<AppError.ValidationError>(error.error)
        assertNull(fileRepository.lastSearchQuery)
    }

    @Test
    fun `search trims query and delegates`() = runTest {
        val result = searchFilesUseCase(query = " plage ", path = "/Photos/")

        assertIs<Resource.Success<*>>(result)
        assertEquals("plage", fileRepository.lastSearchQuery?.query)
        assertEquals("Photos", fileRepository.lastSearchQuery?.path)
    }
}
