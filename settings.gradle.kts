rootProject.name = "aichallenge_one"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
        mavenCentral()
    }
}

include(":composeApp")
include(":server")
include(":shared")

// =============================================================================
// MCP Modules - Disabled for minimal VDS deployment (see SPEC-MCP-VDSINA.md)
// =============================================================================
// include(":mcp:notes")
// include(":mcp:client")
// include(":mcp:newsapi")
// include(":mcp:newscrud")
// include(":mcp:notes-polling")
// include(":mcp:rag")
// include(":mcp:git")
// include(":mcp:github-reviewer")
// include(":mcp:tickets")

// VDSina MCP - Active for VDS management and deployment
include(":mcp:vdsina")

// =============================================================================
// Services Modules - Disabled for minimal VDS deployment (see SPEC-MCP-VDSINA.md)
// =============================================================================
// include(":services:notes")
// include(":services:news-crud")
// include(":services:notes-scheduler")
// include(":services:vectorizer")
// include(":services:rag")
// include(":services:github-webhook")
