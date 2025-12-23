add new module "rag-service" to "services" folder
use kotlin, ktor, koin and exposed.
This module must expose http endpoint that accepts string, searches similar vectors in local postgres-pgvector
and return them as list of strings
