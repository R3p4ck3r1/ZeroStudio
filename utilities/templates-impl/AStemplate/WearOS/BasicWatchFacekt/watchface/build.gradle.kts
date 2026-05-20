plugins { alias(libs.plugins.android.application) }

android {
  namespace = "com.example.basicwatchfacekt"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example.basicwatchfacekt"
    minSdk = 36
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes { release { optimization { enable = false } } }
  enableKotlin = false
}

dependencies {}
