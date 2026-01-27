package ru.sber.cb.aichallenge_one.vectorizer.service.repository

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.ignore.IgnoreNode
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

/**
 * Service for handling .gitignore pattern matching using JGit.
 * Loads patterns from multiple sources:
 * 1. Repository .gitignore file
 * 2. .git/info/exclude (per-repo excludes)
 * 3. Global gitignore (from git config, if configured)
 */
class GitIgnoreService {
    private val logger = LoggerFactory.getLogger(GitIgnoreService::class.java)

    /**
     * Loads gitignore patterns from a Git repository.
     */
    fun loadIgnorePatterns(repoPath: File): GitIgnorePatterns {
        val patterns = mutableListOf<IgnoreNode>()

        try {
            // 1. Load .gitignore from repository root
            val gitignoreFile = File(repoPath, ".gitignore")
            if (gitignoreFile.exists() && gitignoreFile.isFile) {
                val node = IgnoreNode()
                FileInputStream(gitignoreFile).use { stream ->
                    node.parse(stream)
                }
                patterns.add(node)
                logger.debug("Loaded patterns from .gitignore")
            }

            // 2. Load .git/info/exclude
            val excludeFile = File(repoPath, ".git/info/exclude")
            if (excludeFile.exists() && excludeFile.isFile) {
                val node = IgnoreNode()
                FileInputStream(excludeFile).use { stream ->
                    node.parse(stream)
                }
                patterns.add(node)
                logger.debug("Loaded patterns from .git/info/exclude")
            }

            // 3. Try to load global gitignore (if configured)
            try {
                val git = Git.open(repoPath)
                val config = git.repository.config
                val globalIgnorePath = config.getString("core", null, "excludesfile")

                if (globalIgnorePath != null) {
                    val expandedPath = globalIgnorePath.replace("~", System.getProperty("user.home"))
                    val globalIgnoreFile = File(expandedPath)

                    if (globalIgnoreFile.exists() && globalIgnoreFile.isFile) {
                        val node = IgnoreNode()
                        FileInputStream(globalIgnoreFile).use { stream ->
                            node.parse(stream)
                        }
                        patterns.add(node)
                        logger.debug("Loaded patterns from global gitignore: $expandedPath")
                    }
                }
                git.close()
            } catch (e: Exception) {
                logger.warn("Failed to load global gitignore: ${e.message}")
            }

        } catch (e: Exception) {
            logger.error("Error loading gitignore patterns", e)
        }

        return GitIgnorePatterns(patterns)
    }

    /**
     * Checks if a file should be ignored based on gitignore rules.
     */
    fun isIgnored(file: File, repoRoot: File, patterns: GitIgnorePatterns): Boolean {
        try {
            val relativePath = file.relativeTo(repoRoot).path.replace('\\', '/')
            val isDirectory = file.isDirectory

            // Always ignore .git directory
            if (relativePath.startsWith(".git/") || relativePath == ".git") {
                return true
            }

            // Check against all pattern sets
            for (node in patterns.nodes) {
                val result = node.isIgnored(relativePath, isDirectory)
                when (result) {
                    IgnoreNode.MatchResult.IGNORED -> return true
                    IgnoreNode.MatchResult.NOT_IGNORED -> return false
                    IgnoreNode.MatchResult.CHECK_PARENT -> continue
                    else -> continue
                }
            }

            return false
        } catch (e: Exception) {
            logger.error("Error checking ignore status for file: ${file.absolutePath}", e)
            return false
        }
    }
}

/**
 * Container for multiple IgnoreNode instances from different sources.
 */
data class GitIgnorePatterns(val nodes: List<IgnoreNode>)
