Add new module "vectorizer" at "services" folder.
Use kotlin, ktor and koin.
This module have to accept post http request with local folder path as parameter, go to that folder, get all files.
Все файлы в папке нужно разбить на чанки и создать embedding c помощью локальной ollama, которая принимает запросы
по порту http://localhost:11434/api/embed.
Не используй сторонние фреймворки для работы с ollama, только http запромы через ktor-client.
Сделай вычитку файлов оптимальной.
полученные векторы сохраняй в БД postgres-pgvector, поднятую в docker