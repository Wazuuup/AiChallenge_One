# AiChallenge_One Documentation

Welcome to the comprehensive documentation for AiChallenge_One - a Kotlin Multiplatform web application for AI-powered
chat with RAG (Retrieval-Augmented Generation) capabilities.

## 📚 Documentation Structure

- **[Getting Started](GETTING-STARTED.md)** - Quick start guide and setup instructions
- **[Architecture](ARCHITECTURE.md)** - System architecture overview
- **[Deployment](deployment/DEPLOYMENT.md)** - Production deployment guide

## 🏗️ Project Modules

### Core Application

- **[Server](modules/server.md)** - Main AI chat server (port 8080)
- **[Frontend](modules/frontend.md)** - Compose Multiplatform web UI
- **[Shared](modules/shared.md)** - Common data models

### Backend Services

- **[Notes Service](modules/services/notes.md)** - Notes CRUD API (port 8084)
- **[News CRUD Service](modules/services/news-crud.md)** - News articles API (port 8087)
- **[Vectorizer Service](modules/services/vectorizer.md)** - Text embedding generation (port 8090)
- **[RAG Service](modules/services/rag.md)** - Retrieval-Augmented Generation (port 8091)

### MCP Servers

- **[MCP Notes](modules/mcp/notes.md)** - Notes + Currency MCP server (ports 8082/8443)
- **[MCP NewsAPI](modules/mcp/newsapi.md)** - NewsAPI.org integration (ports 8085/8444)
- **[MCP NewsCRUD](modules/mcp/newscrud.md)** - News CRUD proxy (ports 8086/8445)
- **[MCP Notes Polling](modules/mcp/notes-polling.md)** - Docker scheduler management (ports 8088/8447)

## 🛠️ Technology Stack

- **Kotlin**: 2.2.21
- **Compose Multiplatform**: 1.9.1
- **Ktor**: 3.3.3 (Server: Netty, Client: CIO)
- **Koin DI**: 4.1.0
- **Kotlinx Serialization**: 1.8.0
- **PostgreSQL** with pgvector extension
- **Docker** for service deployment

## 🚀 Quick Start

```bash
# Clone the repository
git clone <repository-url>
cd AiChallenge_One

# Set up environment variables
set GIGACHAT_CLIENT_ID=your-id
set GIGACHAT_CLIENT_SECRET=your-secret

# Build and run
.\gradlew.bat build
.\gradlew.bat :server:run
```

## 📋 Port Allocation

| Service           | HTTP Port | HTTPS Port | Description          |
|-------------------|-----------|------------|----------------------|
| Server            | 8080      | -          | AI Chat Server       |
| MCP Notes         | 8082      | 8443       | Notes + Currency     |
| Notes Service     | 8084      | -          | Notes CRUD API       |
| MCP NewsAPI       | 8085      | 8444       | NewsAPI Integration  |
| MCP NewsCRUD      | 8086      | 8445       | News CRUD Proxy      |
| News CRUD         | 8087      | -          | News CRUD API        |
| MCP Notes Polling | 8088      | 8447       | Scheduler Management |
| Vectorizer        | 8090      | -          | Text Embedding       |
| RAG Service       | 8091      | -          | RAG Search           |

## 📖 Additional Resources

- **[Task History](tasks/)** - Historical development tasks
- **[Claude Code Guide](.claude/CLAUDE.md)** - Project instructions for Claude Code

## 🤝 Contributing

This is a learning/demo project showcasing Kotlin Multiplatform, AI integration, and RAG capabilities.

## ⚠️ Security Notice

**DEVELOPMENT ONLY** - This project uses development configurations and is not production-ready.
See [Deployment Guide](deployment/DEPLOYMENT.md) for production considerations.
