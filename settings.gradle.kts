pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // Xposed API 只在这里有（模块用 compileOnly，不会进 APK）
        maven(url = "https://api.xposed.info/")
    }
}

rootProject.name = "TraeWatch"
include(":app")
include(":xposed")
