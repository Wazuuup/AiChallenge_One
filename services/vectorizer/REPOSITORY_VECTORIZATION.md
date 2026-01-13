# Repository Vectorization Feature

## Overview

The Repository Vectorization feature allows you to index entire Git repositories for semantic search using RAG (
Retrieval-Augmented Generation). It intelligently processes repositories by:

- ✅ **Respecting .gitignore patterns** - Automatically skips ignored files
- ✅ **Detecting sensitive data** - Prevents indexing of API keys, passwords, private keys
- ✅ **Security validation** - Path traversal protection, configurable whitelists
- ✅ **Git integration** - Extracts repository metadata (branch, commit, remote URL)

## Endpoint

### `POST /api/vectorizeRepository`

Vectorizes all text files in a Git repository, creating embeddings for semantic search.

## Request Format

```json
{
  "repositoryPath": "C:\\Users\\singe\\IdeaProjects\\MyProject",
  "model": "nomic-embed-text",
  "respectGitIgnore": true,
  "scanForSecrets": true,
  "skipFilesWithSecrets": true,
  "maxFiles": 5000,
  "maxFileSizeMb": 5
}
```

### Request Parameters

| Parameter              | Type    | Required | Default              | Description                             |
|------------------------|---------|----------|----------------------|-----------------------------------------|
| `repositoryPath`       | string  | Yes      | -                    | Absolute path to Git repository         |
| `model`                | string  | No       | `"nomic-embed-text"` | Ollama embedding model                  |
| `respectGitIgnore`     | boolean | No       | `true`               | Skip files matching .gitignore patterns |
| `scanForSecrets`       | boolean | No       | `true`               | Scan files for sensitive data           |
| `skipFilesWithSecrets` | boolean | No       | `true`               | Skip files containing detected secrets  |
| `maxFiles`             | integer | No       | From config          | Maximum number of files to process      |
| `maxFileSizeMb`        | integer | No       | From config          | Maximum file size in MB                 |

## Response Format

```json
{
  "success": true,
  "filesProcessed": 142,
  "chunksCreated": 1834,
  "filesSkipped": [
    {
      "path": "C:\\MyProject\\node_modules\\package\\index.js",
      "reason": "gitignore",
      "details": "File matches .gitignore pattern"
    },
    {
      "path": "C:\\MyProject\\config\\secrets.yaml",
      "reason": "sensitive_data",
      "details": "Contains 2 potential secret(s)"
    }
  ],
  "errors": [],
  "message": "Successfully vectorized 142 files (1834 chunks)",
  "metrics": {
    "durationMs": 45230,
    "totalSizeBytes": 3145728,
    "filesScanned": 250
  },
  "repositoryInfo": {
    "branch": "main",
    "commitHash": "abc123def456789...",
    "remoteUrl": "https://github.com/user/repo.git"
  }
}
```

### Response Fields

| Field            | Type    | Description                                   |
|------------------|---------|-----------------------------------------------|
| `success`        | boolean | Overall operation success (true if no errors) |
| `filesProcessed` | integer | Number of files successfully indexed          |
| `chunksCreated`  | integer | Total number of text chunks created           |
| `filesSkipped`   | array   | List of skipped files with reasons            |
| `errors`         | array   | List of error messages                        |
| `message`        | string  | Human-readable summary                        |
| `metrics`        | object  | Performance metrics                           |
| `repositoryInfo` | object  | Git repository metadata                       |

### Skip Reasons

| Reason             | Description                          |
|--------------------|--------------------------------------|
| `gitignore`        | File matches .gitignore pattern      |
| `sensitive_data`   | File contains detected secrets       |
| `sensitive_file`   | File has sensitive extension or name |
| `too_large`        | File exceeds size limit              |
| `binary`           | Not a recognized text file type      |
| `read_error`       | Failed to read file                  |
| `no_chunks`        | File produced no chunks              |
| `embedding_failed` | Failed to generate embeddings        |

## Usage Examples

### Basic Usage

```bash
curl -X POST http://localhost:8090/api/vectorizeRepository \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryPath": "C:\\Users\\singe\\IdeaProjects\\AiChallenge_One"
  }'
```

### Advanced Usage

```bash
curl -X POST http://localhost:8090/api/vectorizeRepository \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryPath": "C:\\Users\\singe\\IdeaProjects\\AiChallenge_One",
    "model": "nomic-embed-text",
    "respectGitIgnore": true,
    "scanForSecrets": true,
    "skipFilesWithSecrets": true,
    "maxFiles": 10000,
    "maxFileSizeMb": 5
  }'
```

### With PowerShell

```powershell
$body = @{
    repositoryPath = "C:\Users\singe\IdeaProjects\AiChallenge_One"
    model = "nomic-embed-text"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8090/api/vectorizeRepository" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body
```

## Configuration

### application.conf

```hocon
repository {
  # Security: Whitelist allowed base directories (empty = allow all)
  allowedBasePaths = [
    "C:\\Users\\singe\\repos",
    "C:\\Users\\singe\\IdeaProjects"
  ]

  # Resource limits
  maxFiles = 10000
  maxFileSizeMb = 5
  maxTotalSizeMb = 500
  maxDepth = 50

  # Security scanning
  scanForSecrets = true
  skipFilesWithSecrets = true
}
```

### Environment Variables

```bash
# Allowed repository paths (comma-separated)
set REPO_ALLOWED_PATHS=C:\Users\singe\repos,C:\Projects

# Resource limits
set REPO_MAX_FILES=10000
set REPO_MAX_FILE_SIZE_MB=5
set REPO_MAX_TOTAL_SIZE_MB=500
set REPO_MAX_DEPTH=50

# Security
set REPO_SCAN_FOR_SECRETS=true
set REPO_SKIP_FILES_WITH_SECRETS=true
```

## Security Features

### 1. Path Traversal Protection

Prevents accessing files outside allowed directories:

```
❌ BLOCKED: ../../etc/passwd
❌ BLOCKED: C:\Windows\System32\config
✅ ALLOWED: C:\Users\singe\repos\MyProject
```

### 2. Sensitive Data Detection

Automatically detects and skips files containing:

- **API Keys**: `api_key`, `apikey`, `API_KEY` patterns
- **Passwords**: `password`, `passwd`, `pwd` in assignments
- **AWS Credentials**: `AKIA...` access keys
- **Private Keys**: PEM, RSA, DSA key files
- **Database URLs**: JDBC/Postgres URLs with embedded credentials
- **GitHub Tokens**: `ghp_...`, `gho_...` patterns
- **Bearer Tokens**: Authorization headers
- **OpenAI Keys**: `sk-...` patterns
- **Base64 Secrets**: Long base64 strings (likely secrets)

### 3. Sensitive File Detection

Automatically skips files with sensitive extensions or names:

**Extensions**: `.pem`, `.key`, `.p12`, `.pfx`, `.jks`, `.cer`, `.crt`

**Names**: `.env*`, `credentials.*`, `secrets.*`, `id_rsa`, `.npmrc`, `.pypirc`

### 4. GitIgnore Integration

Respects all gitignore sources:

1. Repository `.gitignore`
2. `.git/info/exclude`
3. Global gitignore (from git config)

## Supported File Types

The following text file extensions are indexed:

```
txt, md, kt, java, scala, py, js, ts, json, xml, yaml, yml,
properties, conf, gradle, kts, html, css, sql, sh, bat, c, cpp,
h, hpp, go, rs, rb, php, swift, m, mm, cs, vb, r, jl
```

## Limitations

### Resource Limits (Default)

- **Max Files**: 10,000 files per repository
- **Max File Size**: 5 MB per file
- **Max Total Size**: 500 MB cumulative
- **Max Directory Depth**: 50 levels

### Performance Considerations

- Large repositories (1000+ files) may take several minutes
- Each chunk requires an API call to Ollama
- Database insertion is batched for performance
- Sequential processing (parallel processing in future versions)

### Estimated Processing Times

| Repository Size | Files    | Estimated Time         |
|-----------------|----------|------------------------|
| Small           | 10-100   | 30 seconds - 2 minutes |
| Medium          | 100-500  | 2-10 minutes           |
| Large           | 500-1000 | 10-20 minutes          |
| Very Large      | 1000+    | 20+ minutes            |

## Troubleshooting

### Error: "Path outside allowed directories"

**Solution**: Add the repository path to `allowedBasePaths` in configuration:

```hocon
repository {
  allowedBasePaths = ["C:\\Users\\singe\\repos"]
}
```

### Error: "Not a Git repository"

**Cause**: The directory doesn't have a `.git` folder.

**Solution**: Initialize git in the directory:

```bash
cd C:\path\to\your\project
git init
```

### Files Not Being Indexed

**Possible Causes**:

1. File matches `.gitignore` pattern
2. File extension not in supported list
3. File contains sensitive data (check `filesSkipped` in response)
4. File exceeds size limit

**Solution**: Check the `filesSkipped` array in the response for details.

### High Number of Skipped Files

If many files are skipped due to sensitive data:

1. Review the patterns being detected
2. Consider setting `skipFilesWithSecrets: false` if false positives
3. Add explicit `.gitignore` patterns for sensitive files
4. Review code for hardcoded secrets (security risk!)

## Integration with RAG

After vectorizing a repository, use the RAG search endpoint:

```bash
# Search for similar code/documentation
curl -X POST http://localhost:8091/api/rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "How do I implement authentication?",
    "limit": 5
  }'
```

The search will return relevant code chunks from the vectorized repository.

## Best Practices

### 1. Security

- ✅ Always use `scanForSecrets: true` in production
- ✅ Configure `allowedBasePaths` to restrict access
- ✅ Review `filesSkipped` for unexpected sensitive data
- ✅ Never disable sensitive data detection for public code

### 2. Performance

- ✅ Vectorize during off-peak hours for large repos
- ✅ Use appropriate `maxFiles` limit for your use case
- ✅ Monitor Ollama capacity and rate limits
- ✅ Consider incremental updates instead of full re-indexing

### 3. Maintenance

- ✅ Re-vectorize after significant code changes
- ✅ Clean up old embeddings before re-vectorizing
- ✅ Monitor database size and performance
- ✅ Set up alerts for failed vectorizations

## API Error Codes

| Status Code               | Description                                 |
|---------------------------|---------------------------------------------|
| 200 OK                    | Success (check `success` field in response) |
| 206 Partial Content       | Completed with some errors                  |
| 400 Bad Request           | Invalid request (empty path, etc.)          |
| 403 Forbidden             | Security violation (path not allowed)       |
| 500 Internal Server Error | Critical error during processing            |

## Testing

### Run Unit Tests

```bash
.\gradlew.bat :services:vectorizer:test
```

### Manual Testing

1. Start the vectorizer service:

```bash
.\gradlew.bat :services:vectorizer:run
```

2. Start Ollama with embedding model:

```bash
ollama pull nomic-embed-text
```

3. Test with a small repository:

```bash
curl -X POST http://localhost:8090/api/vectorizeRepository \
  -H "Content-Type: application/json" \
  -d '{"repositoryPath": "C:\\path\\to\\small\\repo"}'
```

## Future Enhancements

- [ ] Parallel processing for performance
- [ ] Incremental updates (only changed files)
- [ ] Progress tracking endpoint
- [ ] Webhook integration for auto-indexing
- [ ] Branch/commit selection
- [ ] Custom file type filters
- [ ] Advanced pattern customization
- [ ] Multi-repository management

## Support

For issues or questions:

- Check the logs: `services/vectorizer/logs/`
- Review configuration: `services/vectorizer/src/main/resources/application.conf`
- GitHub Issues: [Create an issue](https://github.com/your-repo/issues)
