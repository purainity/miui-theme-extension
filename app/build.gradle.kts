plugins {
    alias(libs.plugins.android.application)
}

val releaseStoreFile = providers.gradleProperty("ANDROID_RELEASE_KEYSTORE_FILE").orNull?.let { project.file(it) }
val releaseStorePassword = providers.gradleProperty("ANDROID_RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("ANDROID_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("ANDROID_RELEASE_KEY_PASSWORD").orNull

val releaseSigningAvailable = releaseStoreFile?.isFile == true && !releaseStorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

// 仅在对应变体的 APK 打包任务进入任务图时输出签名状态
listOf("Debug", "Release").forEach { variant ->
    val message = when {
        releaseSigningAvailable -> "Signing properties are configured, ${variant.lowercase()} builds will use the release signing"
        variant == "Debug" -> "Signing properties are not fully configured, debug builds will use the default debug signing"
        else -> "Signing properties are not fully configured, release builds will be unsigned"
    }
    val reportSigning = tasks.register("report${variant}Signing") {
        doLast { logger.lifecycle(message) }
    }
    tasks.matching { it.name == "package$variant" }.configureEach {
        dependsOn(reportSigning)
    }
}

android {
    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = checkNotNull(releaseStoreFile)
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    namespace = "me.purainity.miuithemeextension"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "me.purainity.miuithemeextension"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
            }
        }
        debug {
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
