1) Add to server possibility to store message history to persistent storage. Pick the best suitable for this purpose
2) Messages should be saved to storage with every answer from llm (both gigachat and openrouter, make it universal)
3) When server servers strats it should fetch message history from persistent storage to in-memory cache
4) When ui starts (or page is refreshed) it should fetch message history from server and show it in chat window
5) When user presses New char button history should be cleared from inmemory cache and persistent storage
6) Wrap all applications (persistent storage, server and ui) to docker containers and prepare a docker-compose
   configuration to run them
7) When chat history is summarized it should also be replaced in persistent storage