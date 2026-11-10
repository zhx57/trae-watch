plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.trae.watch.xposed"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trae.watch.xposed"
        minSdk = 27
        targetSdk = 33
        versionCode = 4
        versionName = "1.0.3"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("SIGNING_STORE_FILE")
                ?: "${rootDir}/_signing/release.p12"
            val storePwd = System.getenv("SIGNING_STORE_PASSWORD") ?: "TraeWatch2026!Sign"
            val keyAliasEnv = System.getenv("SIGNING_KEY_ALIAS") ?: "trae-watch"
            val keyPwd = System.getenv("SIGNING_KEY_PASSWORD") ?: storePwd
            storeFile = file(storePath)
            storePassword = storePwd
            keyAlias = keyAliasEnv
            keyPassword = keyPwd
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Xposed 模块不需要自己的 Activity，但 Android 要求有 launcher 入口才方便激活
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Xposed API 不打包进 APK，运行时由框架提供
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")
}
