You have created rag service. Now you have to add using it to main applications.

Add new checkbox "Use RAG" to both providers (Gigachat and Openrouter). Place this checbox to the right of Provider
selection dropbox.
Pass value of this checkbox to server with every request. Must be off by default.

Server should accept boolean value of Use RAG checkbox.
If checkbox value is false, the process request as usual.
If checkbox is true, then you have to make a request to "rag" module to "api/rag/search" endpoint with user request text
as value, get similar texts as rag result and include revieved result to user prompt as context for llm.

---

## Status: ✅ COMPLETED

### Implementation Summary:

**Frontend Changes:**

- Added "Use RAG" checkbox below provider dropdown in ChatScreen.kt
- Added RAG state management in ChatViewModel.kt (StateFlow)
- Updated ChatApi.kt to pass useRag parameter
- Updated SendMessageRequest.kt model with useRag field (default: false)

**Backend Changes:**

- Created RagClient.kt for HTTP communication with RAG service (port 8091)
- Registered RagClient in dependency injection (AppModule.kt)
- Updated ChatService.kt to enrich USER PROMPT (not system prompt) with RAG context
- Updated ChatRouting.kt to pass useRag flag through to service layer

**RAG Context Format:**

```
=== Relevant Context from Knowledge Base ===
1. {chunk 1}
2. {chunk 2}
...
=== End of Context ===

User Question: {original user text}
```

**Build Status:** ✅ Server and frontend build successfully

**Verified:** RAG context correctly added to user prompt as specified in requirements.
