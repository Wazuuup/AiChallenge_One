package ru.sber.cb.aichallenge_one.mcp_github_reviewer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GitHub
import org.kohsuke.github.GitHubBuilder
import org.slf4j.LoggerFactory
import java.io.IOException

class GitHubService(private val githubToken: String) {
    private val logger = LoggerFactory.getLogger(GitHubService::class.java)

    private val github: GitHub = GitHubBuilder()
        .withOAuthToken(githubToken)
        .build()

    /**
     * Получить diff для Pull Request
     */
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): String {
        return withContext(Dispatchers.IO) {
            try {
                val repository = github.getRepository("$owner/$repo")
                val pullRequest = repository.getPullRequest(prNumber)

                // GitHub API возвращает diff через специальный метод
                val diffUrl = pullRequest.diffUrl
                logger.info("Fetching diff for PR #$prNumber from $diffUrl")

                // Получаем diff через HTTP
                val connection = diffUrl.openConnection()
                connection.setRequestProperty("Authorization", "Bearer $githubToken")
                connection.setRequestProperty("Accept", "application/vnd.github.v3.diff")

                val diff = connection.getInputStream().bufferedReader().use { it.readText() }

                logger.info("Successfully fetched diff for PR #$prNumber (${diff.length} chars)")
                diff
            } catch (e: GHFileNotFoundException) {
                logger.error("Pull request #$prNumber not found in $owner/$repo")
                throw IllegalArgumentException("Pull request #$prNumber not found in $owner/$repo")
            } catch (e: IOException) {
                logger.error("Error fetching diff for PR #$prNumber", e)
                throw RuntimeException("Failed to fetch diff: ${e.message}", e)
            }
        }
    }

    /**
     * Оставить комментарий в Pull Request
     */
    suspend fun postComment(owner: String, repo: String, prNumber: Int, body: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val repository = github.getRepository("$owner/$repo")
                val pullRequest = repository.getPullRequest(prNumber)

                logger.info("Posting comment to PR #$prNumber in $owner/$repo")

                // Используем issue API для комментариев (PR = Issue в GitHub API)
                pullRequest.comment(body)

                logger.info("Successfully posted comment to PR #$prNumber")
                true
            } catch (e: GHFileNotFoundException) {
                logger.error("Pull request #$prNumber not found in $owner/$repo")
                throw IllegalArgumentException("Pull request #$prNumber not found in $owner/$repo")
            } catch (e: IOException) {
                logger.error("Error posting comment to PR #$prNumber", e)
                throw RuntimeException("Failed to post comment: ${e.message}", e)
            }
        }
    }

    /**
     * Получить содержимое файла из репозитория (с ограничением 1000 строк)
     */
    suspend fun getFileContent(owner: String, repo: String, path: String, ref: String?): String {
        return withContext(Dispatchers.IO) {
            try {
                val repository = github.getRepository("$owner/$repo")

                logger.info("Fetching file content: $path from $owner/$repo (ref: $ref)")

                // Получаем содержимое файла
                val content = if (ref != null) {
                    repository.getFileContent(path, ref)
                } else {
                    repository.getFileContent(path)
                }

                val fileContent = String(content.read().readBytes())

                // Ограничение 1000 строк
                val lines = fileContent.lines()
                val result = if (lines.size > 1000) {
                    val truncated = lines.take(1000).joinToString("\n")
                    logger.warn("File $path truncated to 1000 lines (original: ${lines.size} lines)")
                    "$truncated\n\n... (file truncated, original size: ${lines.size} lines)"
                } else {
                    fileContent
                }

                logger.info("Successfully fetched file content: $path (${lines.size} lines)")
                result
            } catch (e: GHFileNotFoundException) {
                logger.error("File $path not found in $owner/$repo")
                throw IllegalArgumentException("File $path not found in $owner/$repo")
            } catch (e: IOException) {
                logger.error("Error fetching file content: $path", e)
                throw RuntimeException("Failed to fetch file content: ${e.message}", e)
            }
        }
    }

    /**
     * Проверить валидность GitHub token
     */
    suspend fun validateToken(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                github.myself
                logger.info("GitHub token validated successfully")
                true
            } catch (e: IOException) {
                logger.error("Invalid GitHub token", e)
                false
            }
        }
    }
}
