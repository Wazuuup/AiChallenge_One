Add new service in server module, that will have function, that have to be run every 2 minutes by cron.
This unction have to send a message to OpenRouter (model gpt-oss-120b (free)) with all available from mcp-server tools.
System prompt for message: Ты личный помощник, планировщик задач
User prompt: Предоставь краткое summary по заметкам

Add new feature to UI: possibility to render push notifications.
New cron function at server have to call new UI API and render text from response of the llm for summarization report