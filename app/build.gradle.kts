plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------------
// Signing is supplied at build time from GitHub Actions secrets, never from
// the repository. Nothing secret is committed: the workflow decodes the
// keystore into $RUNNER_TEMP and passes these env vars to Gradle.
// ---------------------------------------------------------------------------
val ksFile = System.getenv("READLOOP_KEYSTORE_FILE")
val ksStorePass = System.getenv("READLOOP_KEYSTORE_PASSWORD")
val ksAlias = System.getenv("READLOOP_KEY_ALIAS")
val ksKeyPass = System.getenv("READLOOP_KEY_PASSWORD") ?: ksStorePass

val canSign = !ksFile.isNullOrBlank() &&
    file(ksFile).exists() &&
    !ksStorePass.isNullOrBlank() &&
    !ksAlias.isNullOrBlank()

// Every CI run gets a higher versionCode so the phone accepts the update.
val buildNumber = System.getenv("READLOOP_BUILD_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.dol.readloop"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dol.readloop"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = ksStorePass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (canSign) {
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
}

// No external dependencies: framework-only so the build stays fast and stable.
