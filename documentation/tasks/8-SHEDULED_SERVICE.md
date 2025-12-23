Modify server module. Now this module sends notes summary from db to UI as a notification every 2 minutes.
Disable scheduling for sending notes summary to UI and add new http endpoint "trigger summary to ui" that triggers
getting notes summary from db and sending it to UI.

-------------

Add new module "notes-scheduler" in "services" folder
When service starts it begins to make http requests to "trigger summary to ui" endpoint at server
Use kotlin and ktor-client. Use cron-expression from HOCON configuration to set frequency (default value is every 2
minutes)
Make docker file for this server.
-------------


add new mcp server "notes-polling" to "mcp" folder
This mcp should start at 8087 and 8446 ports for http and https.

Add two tools to this mcp:

1) trigger notes summary polling
2) stop notes summary polling

When first tool is called this module must build and start "notes-scheduler" as a container at local docker
when second tool is called this module must stop "notes-scheduler" container at local docker