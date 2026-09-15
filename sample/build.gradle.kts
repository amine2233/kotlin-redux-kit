plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.amine2233.redux.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.amine2233.redux.sample"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = project.version.toString()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions.unitTests.all { it.useJUnitPlatform() }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":redux"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation(project(":redux-test"))
    testImplementation(libs.kotlin.test)
}
