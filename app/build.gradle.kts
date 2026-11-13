import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hanzdev.susunkata"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hanzdev.susunkata"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Penandatanganan RELEASE: dibaca dari variabel lingkungan (diisi GitHub Actions)
    // atau dari keystore.properties (lokal, jangan di-commit).
    val props = Properties()
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }
    fun cfg(envName: String, key: String): String? =
        System.getenv(envName) ?: props.getProperty(key)

    signingConfigs {
        create("release") {
            val path = cfg("KEYSTORE_PATH", "storeFile")
            if (path != null) {
                storeFile = file(path)
                storePassword = cfg("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = cfg("KEY_ALIAS", "keyAlias")
                keyPassword = cfg("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (cfg("KEYSTORE_PATH", "storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        noCompress += listOf("ogg", "wav")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
