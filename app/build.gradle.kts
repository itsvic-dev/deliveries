// SPDX-License-Identifier: GPL-3.0-or-later
import java.io.FileInputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists())
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))

room { schemaDirectory("$projectDir/schemas") }

android {
  namespace = "dev.itsvic.parceltracker"
  compileSdk = 37

  defaultConfig {
    applicationId = "dev.itsvic.parceltracker"
    minSdk = 26
    targetSdk = 37
    // ((major * 100 + minor) * 100 + patch) * 1000 + build
    versionCode = 10501000
    versionName = "1.5.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (keystorePropertiesFile.exists())
        create("release") {
          keyAlias = keystoreProperties["keyAlias"] as String
          keyPassword = keystoreProperties["keyPassword"] as String
          storeFile = file(keystoreProperties["storeFile"] as String)
          storePassword = keystoreProperties["storePassword"] as String
        }
  }

  buildTypes {
    release {
      if (keystorePropertiesFile.exists()) {
        signingConfig = signingConfigs.getByName("release")
      }

      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    debug { isPseudoLocalesEnabled = true }
  }

  androidResources { generateLocaleConfig = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  // Disables encrypted dependency info block as requested by the F-Droid team.
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

androidComponents {
  beforeVariants(selector().withBuildType("release")) {
    (it as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons)
  implementation(libs.okhttp)
  implementation(libs.okhttp.coroutines)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi)
  implementation(libs.jsoup)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.navigation.fragment)
  implementation(libs.androidx.navigation.ui)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.work.runtime)
  implementation(libs.work.runtime.ktx)
  implementation(libs.kotlinx.coroutines.guava)
  implementation(libs.androidx.browser)

  ksp(libs.room.compiler)
  ksp(libs.moshi.kotlin.codegen)

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}
