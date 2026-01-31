plugins {
    id("com.android.dynamic-feature")
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

android {
    namespace = "com.matrix.multigpt.localinference"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        proguardFiles("proguard-rules.pro")
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
        viewBinding = true
    }
}

dependencies {
    implementation(project(":app"))

    // Llama Kotlin Android for local inference
    implementation(libs.llama.kotlin.android)

    // Hilt
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose (optional - for Compose UI)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.viewmodel)
    implementation(libs.androidx.navigation)
    implementation(libs.androidx.lifecycle.runtime.compose.android)

    // AppCompat for base Activity support
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Serialization
    implementation(libs.kotlin.serialization)

    // Ktor for model downloads
    implementation(libs.ktor.core)
    implementation(libs.ktor.engine)

    // DataStore
    implementation(libs.androidx.datastore)

    // Firebase Database (from base app via BOM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
