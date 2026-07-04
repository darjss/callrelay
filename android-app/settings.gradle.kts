// The gradle wrapper (gradlew, gradlew.bat, gradle-wrapper.jar) is not
// committed. Run `gradle wrapper` once the Android SDK is installed to
// generate them; gradle/wrapper/gradle-wrapper.properties is already in place.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "CallRelay"
include(":app")
