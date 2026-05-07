import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
  alias(libs.plugins.org.jetbrains.kotlin.plugin.compose)
}

android {
  namespace = "me.rerere.ai"
  compileSdk = 36

  defaultConfig {
    minSdk = 26

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
    //        externalNativeBuild {
    //            cmake {
    //                cppFlags += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
    //                abiFilters += listOf("arm64-v8a", "x86_64")
    //            }
    //        }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures { compose = true }
  //    externalNativeBuild {
  //        cmake {
  //            path = file("src/main/cpp/CMakeLists.txt")
  //            version = "3.22.1"
  //        }
  //    }
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
    compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
  }
}

dependencies {
  implementation(projects.core.common)

  implementation(libs.androidx.core.ktx)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)

  api(libs.okhttp)
  api(libs.okhttp.sse)
  api(libs.okhttp.logging)

  api(libs.kotlinx.serialization.json)
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.datetime)

  testImplementation(libs.tests.junit)
  androidTestImplementation(libs.tests.androidx.junit)
  androidTestImplementation(libs.tests.androidx.espresso.core)
}
