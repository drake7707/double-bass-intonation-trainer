import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.devtools.ksp)

    id("dev.detekt")
}

ksp {
    // Export Room schemas so migrations are validated against the generated schema history.
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "be.drakarah.intonation"
    compileSdk = 36

    defaultConfig {
        applicationId = "be.drakarah.intonation"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // detection code lifted from Tuner calls android.util.Log; let it no-op on the JVM
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("test") {
            // real-bass and noise recordings live in :dsp's corpus; game tests replay them too
            resources.srcDir("../dsp/src/test/resources")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":dsp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // per-app locale backport (AppCompatDelegate) for < API 33
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

detekt {
    buildUponDefaultConfig = true
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}