plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.trae.watch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trae.watch"
        minSdk = 27
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("zh", "en")
    }

    /**
     * 固定签名：CI 从环境变量注入 keystore，本地从 _signing/release.p12 读取。
     * 保证每次构建签名一致，可覆盖安装升级。
     */
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

            // PKCS12 统一用 store password 作为 key password
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "kotlin/**"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
