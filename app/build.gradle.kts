plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.neb.ians"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.neb.note"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            localeFilters += listOf("en", "ne")
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    val ksFile = file("${rootProject.projectDir}/nebians-release.keystore")
    val ksPass = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD") as? String
    val kPass = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD") as? String
    val hasCustomKeystore = ksFile.exists() && !ksPass.isNullOrBlank() && !kPass.isNullOrBlank()

    signingConfigs {
        if (hasCustomKeystore) {
            create("release") {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS") as? String ?: "nebians"
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "NONE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasCustomKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    flavorDimensions += "target"
    productFlavors {
        create("modern") {
            dimension = "target"
            minSdk = 28
            signingConfig = if (hasCustomKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        create("legacy") {
            dimension = "target"
            minSdk = 24
            signingConfig = if (hasCustomKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/META-INF/com/android/build/gradle/**",
                "/META-INF/proguard/**",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/**/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/**/NOTICE*",
                "/okhttp3/internal/publicsuffix/NOTICE",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "**/*.kotlin_builtins",
                "**/*.kotlin_metadata",
                "/*.proto",
                "/*.properties"
            )
        }
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.compose.material3:material3:1.5.0-alpha10")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.1")

    testImplementation("junit:junit:4.13.2")
}
