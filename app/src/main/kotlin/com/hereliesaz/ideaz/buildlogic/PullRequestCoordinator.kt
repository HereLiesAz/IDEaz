package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.api.GitHubApi
import com.hereliesaz.ideaz.api.MergePullRequestRequest

/**
 * Auto-merges a pull request opened by the agent (Jules) and resolves the merge
 * commit SHA — the input the [RemoteBuildManager] needs to poll Actions for the
 * rebuilt APK. This is the "auto-merge" half of the PR-based Android loop.
 */
class PullRequestCoordinator(private val api: GitHubApi) {

    /**
     * Merge the PR identified by [prUrl] and return its merge commit SHA, or null
     * if the URL is unparseable or the merge fails (not mergeable / conflict /
     * network). Idempotent: a PR that is already merged (e.g. on a retry) returns
     * its existing merge commit instead of erroring.
     */
    suspend fun mergeAndGetSha(prUrl: String, mergeMethod: String = "squash"): String? {
        val ref = parsePullRequestUrl(prUrl) ?: return null

        // Already merged (retry / duplicate event): reuse the existing commit.
        val existing = runCatching { api.getPullRequest(ref.owner, ref.repo, ref.number) }.getOrNull()
        if (existing?.merged == true) return existing.mergeCommitSha

        val response = runCatching {
            api.mergePullRequest(
                ref.owner, ref.repo, ref.number,
                MergePullRequestRequest(mergeMethod = mergeMethod)
            )
        }.getOrNull() ?: return null

        if (!response.isSuccessful) return null
        return response.body()?.takeIf { it.merged }?.sha
    }

    data class PrRef(val owner: String, val repo: String, val number: Int)

    companion object {
        // Matches both the html_url (github.com/owner/repo/pull/5) and the API
        // url (api.github.com/repos/owner/repo/pulls/5) shapes Jules may return.
        private val PR_URL = Regex("""github\.com/(?:repos/)?([^/]+)/([^/]+)/pulls?/(\d+)""")

        fun parsePullRequestUrl(url: String): PrRef? {
            val match = PR_URL.find(url) ?: return null
            val (owner, repo, number) = match.destructured
            return PrRef(owner, repo, number.toIntOrNull() ?: return null)
        }
    }
}
