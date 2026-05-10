// Root Gradle settings for Still Contacts.
// Mirrors still-launcher / still-notes / still-cal: one repo declaration, single :app module.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "still-contacts"
include(":app")
