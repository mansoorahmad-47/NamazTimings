pluginManagement {
    repositories {
        // Google first: the Android plugin lives there, so Maven Central is
        // asked for less and is less likely to rate-limit us.
        google()
        gradlePluginPortal()
        mavenCentral()
        // Maven Central under its other hostname. Gradle falls through to the
        // next repository when one refuses, so this rescues the build when
        // repo.maven.apache.org answers 403 to GitHub's shared runner IPs.
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
    // Versions declared once, here, so the root and app build files cannot
    // disagree about them.
    plugins {
        id("com.android.application") version "8.9.1"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo1.maven.org/maven2") }
    }
}

rootProject.name = "NamazTimings"
include(":app")
