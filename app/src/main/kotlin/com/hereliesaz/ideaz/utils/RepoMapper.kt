package com.hereliesaz.ideaz.utils

import com.hereliesaz.ideaz.api.GitHubRepoResponse
import com.hereliesaz.ideaz.api.Source
import com.hereliesaz.ideaz.api.GitHubPermissions

object RepoMapper {
    fun mapSourceToRepoResponse(source: Source): GitHubRepoResponse? {
        val gh = source.githubRepo ?: return null
        val fullName = "${gh.owner}/${gh.repo}"
        val htmlUrl = "https://github.com/$fullName"
        val cloneUrl = "$htmlUrl.git"

        return GitHubRepoResponse(
            id = fullName.hashCode().toLong(),
            name = gh.repo,
            fullName = fullName,
            htmlUrl = htmlUrl,
            cloneUrl = cloneUrl,
            defaultBranch = gh.defaultBranch?.displayName ?: "main",
            permissions = GitHubPermissions(admin = true, push = true, pull = true)
        )
    }
}
