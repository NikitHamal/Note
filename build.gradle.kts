plugins {
    id("com.android.application") version "9.5.1" apply false
    id("com.android.library") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.9")
    }
}
