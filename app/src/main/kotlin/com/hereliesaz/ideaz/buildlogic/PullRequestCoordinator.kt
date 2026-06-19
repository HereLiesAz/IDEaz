package com.hereliesaz.ideaz.buildlogic

import com.hereliesaz.ideaz.api.GitHubApi
import com.hereliesaz.ideaz.api.MergePullRequestRequest
import kotlinx.coroutines.CancellationException

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
        // Catch Exception (not runCatching) so cancellation propagates — otherwise a
        // cancelled rebuild would return null and surface a misleading "Build failed".
        val existing = try {
            api.getPullRequest(ref.owner, ref.repo, ref.number)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (existing?.merged == true) return existing.mergeCommitSha

        val response = try {
            api.mergePullRequest(
                ref.owner, ref.repo, ref.number,
                MergePullRequestRequest(mergeMethod = mergeMethod)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }

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
