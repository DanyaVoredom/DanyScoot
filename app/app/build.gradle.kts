plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.dany.scoot"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.dany.scoot"
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "1.5.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("DANYSCOOT_KEYSTORE") ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = System.getenv("DANYSCOOT_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("DANYSCOOT_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("DANYSCOOT_KEY_PASSWORD") ?: "android"
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
