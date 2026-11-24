import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy


plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}


android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }


  defaultConfig {
    applicationId = "com.aistudio.taskflow.kqzpmv"
    // NFR-008 PRD: minimum Android API 26 (sebelumnya 24).
    minSdk = 26
    targetSdk = 36
    versionCode = 4
    versionName = "1.2.1"


    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }


  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }


  buildTypes {
    release {
      isCrunchPngs = false
      // B-01: R8 + resource shrinking aktif — APK lebih kecil dan lebih sulit
      // di-reverse-engineer. Model Firestore sudah beranotasi @Keep.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
