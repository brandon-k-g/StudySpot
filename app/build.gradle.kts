import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    // Apply the Google services Gradle plugin using Kotlin DSL syntax
    id("com.google.gms.google-services")
}

val localProps = Properties()
val localPropertiesFile = File(rootProject.rootDir,"local.properties")
if (localPropertiesFile.exists() && localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use {
        localProps.load(it)
    }
}
android {
    namespace = "com.example.studyspot"
    compileSdk = 35

    buildFeatures {
        viewBinding = true
        buildConfig = true

    }

    defaultConfig {
        applicationId = "com.example.studyspot"
        minSdk = 24
        targetSdk = 35 // Or 34 if compileSdk is 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // This part for accessing your API key is correct for Kotlin DSL
        val geminiApiKey: String = project.findProperty("GEMINI_API_KEY") as? String ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "GEMINI_API_KEY", localProps.getProperty("GEMINI_API_KEY"))

        }
        debug {
            buildConfigField("String", "GEMINI_API_KEY", localProps.getProperty("GEMINI_API_KEY"))

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {

    implementation(platform(libs.firebase.bom))

    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation("com.google.firebase:firebase-appcheck-debug")

    implementation("com.squareup.okhttp3:okhttp:4.12.0") // Or the latest version

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.room.common.jvm)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

