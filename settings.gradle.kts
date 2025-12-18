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
include(":mcp-server")
include(":mcp-client")
include(":mcp-newsapi")
include(":shared")
include(":notes")
include(":news-crud")
include(":mcp-newscrud")