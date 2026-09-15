import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.box.android"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val customVersionName = (project.findProperty("appVersionName") as? String)?.takeIf { it.isNotBlank() } ?: "1.0.0"
    val customVersionCode = (project.findProperty("appVersionCode") as? String)?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.box.android"
        minSdk = 24
        targetSdk = 28
        versionCode = customVersionCode
        versionName = customVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProperties = Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            FileInputStream(localPropertiesFile).use { load(it) }
        }
    }

    val keystoreFilePath = System.getenv("KEYSTORE_FILE")
        ?: localProperties.getProperty("RELEASE_STORE_FILE")
        ?: (project.findProperty("RELEASE_STORE_FILE") as? String)
        ?: rootProject.file("release.jks").takeIf { it.exists() }?.absolutePath

    val keystoreStorePassword = System.getenv("KEYSTORE_PASSWORD")
        ?: localProperties.getProperty("RELEASE_STORE_PASSWORD")
        ?: (project.findProperty("RELEASE_STORE_PASSWORD") as? String)

    val keystoreKeyAlias = System.getenv("KEY_ALIAS")
        ?: localProperties.getProperty("RELEASE_KEY_ALIAS")
        ?: (project.findProperty("RELEASE_KEY_ALIAS") as? String)
        ?: "box-runner-key"

    val keystoreKeyPassword = System.getenv("KEY_PASSWORD")
        ?: localProperties.getProperty("RELEASE_KEY_PASSWORD")
        ?: (project.findProperty("RELEASE_KEY_PASSWORD") as? String)
        ?: keystoreStorePassword

    signingConfigs {
        if (keystoreFilePath != null && keystoreStorePassword != null && file(keystoreFilePath).exists()) {
            create("release") {
                storeFile = file(keystoreFilePath)
                storePassword = keystoreStorePassword
                keyAlias = keystoreKeyAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.findByName("release")
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.github.luben:zstd-jni:1.5.6-8@aar")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}