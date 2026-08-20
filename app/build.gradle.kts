plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.rankis.openime"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.rankis.openime"
        minSdk = 26
        targetSdk = 35
        versionCode = 23
        versionName = "0.3.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val keystorePath = providers.gradleProperty("OPENIME_RELEASE_STORE_FILE").orNull
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = providers.gradleProperty("OPENIME_RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("OPENIME_RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("OPENIME_RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    applicationVariants.all {
        val variantName = name
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "OpenVoiceIME-${variantName}.apk"
        }
    }
}

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.path.contains("Release")
    }
    if (releaseRequested) {
        val requiredProperties = listOf(
            "OPENIME_RELEASE_STORE_FILE",
            "OPENIME_RELEASE_STORE_PASSWORD",
            "OPENIME_RELEASE_KEY_ALIAS",
            "OPENIME_RELEASE_KEY_PASSWORD",
        )
        val missingProperties = requiredProperties.filter { property ->
            providers.gradleProperty(property).orNull.isNullOrBlank()
        }
        if (missingProperties.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured. Missing: ${missingProperties.joinToString()}",
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
