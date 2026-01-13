package ru.sber.cb.aichallenge_one.mcp_git.service

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for Git operations using JGit library.
 * Provides methods for status, log, diff, branch management, and commits.
 */
class GitService(private val repositoryPath: String) {
    private val logger = LoggerFactory.getLogger(GitService::class.java)

    private val repository: Repository = FileRepositoryBuilder()
        .setGitDir(File(repositoryPath, ".git"))
        .readEnvironment()
        .findGitDir()
        .build()

    private val git: Git = Git(repository)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    /**
     * Get the current status of the repository.
     * Shows modified, added, removed, untracked files.
     */
    fun getStatus(): String {
        return try {
            val status: Status = git.status().call()

            buildString {
                appendLine("Repository Status:")
                appendLine("Current branch: ${repository.branch}")
                appendLine()

                if (status.hasUncommittedChanges() || status.untracked.isNotEmpty()) {
                    // Staged changes
                    if (status.added.isNotEmpty()) {
                        appendLine("Staged files (new):")
                        status.added.forEach { appendLine("  A  $it") }
                        appendLine()
                    }

                    if (status.changed.isNotEmpty()) {
                        appendLine("Staged files (modified):")
                        status.changed.forEach { appendLine("  M  $it") }
                        appendLine()
                    }

                    if (status.removed.isNotEmpty()) {
                        appendLine("Staged files (deleted):")
                        status.removed.forEach { appendLine("  D  $it") }
                        appendLine()
                    }

                    // Unstaged changes
                    if (status.modified.isNotEmpty()) {
                        appendLine("Unstaged changes (modified):")
                        status.modified.forEach { appendLine("  M  $it") }
                        appendLine()
                    }

                    if (status.missing.isNotEmpty()) {
                        appendLine("Unstaged changes (deleted):")
                        status.missing.forEach { appendLine("  D  $it") }
                        appendLine()
                    }

                    // Untracked files
                    if (status.untracked.isNotEmpty()) {
                        appendLine("Untracked files:")
                        status.untracked.forEach { appendLine("  ?? $it") }
                        appendLine()
                    }
                } else {
                    appendLine("Working tree clean")
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting repository status", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get commit history log.
     * @param maxCount Maximum number of commits to retrieve (default: 10)
     */
    fun getLog(maxCount: Int = 10): String {
        return try {
            val logs: Iterable<RevCommit> = git.log().setMaxCount(maxCount).call()

            buildString {
                appendLine("Commit History (last $maxCount commits):")
                appendLine()

                logs.forEach { commit ->
                    appendLine("commit ${commit.name}")
                    appendLine("Author: ${commit.authorIdent.name} <${commit.authorIdent.emailAddress}>")
                    appendLine("Date:   ${dateFormat.format(Date(commit.commitTime * 1000L))}")
                    appendLine()
                    appendLine("    ${commit.fullMessage}")
                    appendLine()
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting commit log", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get diff of unstaged changes.
     */
    fun getDiff(): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            val formatter = DiffFormatter(outputStream)
            formatter.setRepository(repository)

            // Get HEAD tree
            val head = repository.resolve("HEAD^{tree}")
            val oldTreeIter = CanonicalTreeParser()
            repository.newObjectReader().use { reader ->
                oldTreeIter.reset(reader, head)
            }

            // Get current working tree
            val newTreeIter = CanonicalTreeParser()

            // Diff between HEAD and working tree
            val diffs = formatter.scan(oldTreeIter, newTreeIter)

            if (diffs.isEmpty()) {
                "No changes to show"
            } else {
                diffs.forEach { formatter.format(it) }
                formatter.flush()
                outputStream.toString("UTF-8")
            }
        } catch (e: Exception) {
            logger.error("Error getting diff", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get diff of staged changes.
     */
    fun getDiffStaged(): String {
        return try {
            val outputStream = ByteArrayOutputStream()
            val formatter = DiffFormatter(outputStream)
            formatter.setRepository(repository)

            // Get HEAD tree
            val head = repository.resolve("HEAD^{tree}")
            val headTreeIter = CanonicalTreeParser()
            repository.newObjectReader().use { reader ->
                headTreeIter.reset(reader, head)
            }

            // Get staged tree (index)
            val indexTreeIter = CanonicalTreeParser()
            val index = repository.readDirCache()
            val indexTreeId = index.writeTree(repository.newObjectInserter())
            repository.newObjectReader().use { reader ->
                indexTreeIter.reset(reader, indexTreeId)
            }

            // Diff between HEAD and index
            val diffs = formatter.scan(headTreeIter, indexTreeIter)

            if (diffs.isEmpty()) {
                "No staged changes to show"
            } else {
                diffs.forEach { formatter.format(it) }
                formatter.flush()
                outputStream.toString("UTF-8")
            }
        } catch (e: Exception) {
            logger.error("Error getting staged diff", e)
            "Error: ${e.message}"
        }
    }

    /**
     * List all branches in the repository.
     */
    fun listBranches(): String {
        return try {
            val branches: List<Ref> = git.branchList().call()
            val currentBranch = repository.branch

            buildString {
                appendLine("Branches:")
                branches.forEach { ref ->
                    val branchName = ref.name.removePrefix("refs/heads/")
                    val marker = if (branchName == currentBranch) "* " else "  "
                    appendLine("$marker$branchName")
                }
            }
        } catch (e: Exception) {
            logger.error("Error listing branches", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Create a new branch.
     * @param branchName Name of the new branch
     * @param checkout Whether to checkout the new branch immediately
     */
    fun createBranch(branchName: String, checkout: Boolean = false): String {
        return try {
            git.branchCreate()
                .setName(branchName)
                .call()

            val result = "Branch '$branchName' created successfully"

            if (checkout) {
                git.checkout().setName(branchName).call()
                "$result and checked out"
            } else {
                result
            }
        } catch (e: Exception) {
            logger.error("Error creating branch", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Checkout a branch or commit.
     * @param target Branch name or commit hash to checkout
     */
    fun checkout(target: String): String {
        return try {
            git.checkout()
                .setName(target)
                .call()

            "Switched to branch/commit '$target'"
        } catch (e: Exception) {
            logger.error("Error checking out", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Add files to staging area.
     * @param filePattern File pattern to add (e.g., ".", "*.kt", "src/main/")
     */
    fun addFiles(filePattern: String): String {
        return try {
            git.add()
                .addFilepattern(filePattern)
                .call()

            "Added files matching pattern '$filePattern' to staging area"
        } catch (e: Exception) {
            logger.error("Error adding files", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Create a commit with staged changes.
     * @param message Commit message
     * @param author Optional author name (uses Git config if not provided)
     * @param email Optional author email (uses Git config if not provided)
     */
    fun commit(message: String, author: String? = null, email: String? = null): String {
        return try {
            val commitCommand = git.commit().setMessage(message)

            if (author != null && email != null) {
                commitCommand.setAuthor(author, email)
            }

            val commit = commitCommand.call()

            buildString {
                appendLine("Commit created successfully:")
                appendLine("Hash: ${commit.name}")
                appendLine("Author: ${commit.authorIdent.name} <${commit.authorIdent.emailAddress}>")
                appendLine("Message: ${commit.shortMessage}")
            }
        } catch (e: Exception) {
            logger.error("Error creating commit", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get current branch name.
     */
    fun getCurrentBranch(): String {
        return try {
            repository.branch ?: "DETACHED HEAD"
        } catch (e: Exception) {
            logger.error("Error getting current branch", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Show content of a file at a specific commit.
     * @param filePath Path to the file
     * @param commitHash Commit hash (default: HEAD)
     */
    fun showFile(filePath: String, commitHash: String = "HEAD"): String {
        return try {
            val objectId: ObjectId = repository.resolve(commitHash)
            val revWalk = RevWalk(repository)
            val commit = revWalk.parseCommit(objectId)
            val tree = commit.tree

            // Parse the file path
            val reader = repository.newObjectReader()
            val treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(reader, filePath, tree)

            if (treeWalk == null) {
                "File '$filePath' not found in commit $commitHash"
            } else {
                val objectLoader = reader.open(treeWalk.getObjectId(0))
                val bytes = objectLoader.bytes
                String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            logger.error("Error showing file", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Get remote URL if configured.
     */
    fun getRemoteUrl(): String {
        return try {
            val remotes = git.remoteList().call()
            if (remotes.isEmpty()) {
                "No remotes configured"
            } else {
                buildString {
                    appendLine("Remotes:")
                    remotes.forEach { remote ->
                        appendLine("${remote.name}: ${remote.urIs.firstOrNull()?.toString() ?: "No URL"}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting remote URL", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Close the repository connection.
     */
    fun close() {
        repository.close()
        git.close()
    }
}
