package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.api.GitHubRepo
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.api.GitHubBranch
import com.hereliesaz.ideaz.utils.RepoMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RepoMapperTest {

    @Test
    fun mapSourceToRepoResponse_validSource_returnsCorrectResponse() {
        val source = Source(
            name = "projects/123/locations/us-central1/sources/github/owner/repo",
            id = "github/owner/repo",
            githubRepo = GitHubRepo(
                owner = "owner",
                repo = "repo",
                defaultBranch = GitHubBranch("main")
            )
        )

        val result = RepoMapper.mapSourceToRepoResponse(source)

        assertNotNull(result)
        assertEquals("owner/repo", result?.fullName)
        assertEquals("repo", result?.name)
        assertEquals("https://github.com/owner/repo", result?.htmlUrl)
        assertEquals("https://github.com/owner/repo.git", result?.cloneUrl)
        assertEquals("main", result?.defaultBranch)
        assertEquals(true, result?.permissions?.admin)
    }

    @Test
    fun mapSourceToRepoResponse_missingGitHubRepo_returnsNull() {
        val source = Source(
            name = "projects/123/locations/us-central1/sources/other",
            id = "other"
        )

        val result = RepoMapper.mapSourceToRepoResponse(source)

        assertNull(result)
    }
}
