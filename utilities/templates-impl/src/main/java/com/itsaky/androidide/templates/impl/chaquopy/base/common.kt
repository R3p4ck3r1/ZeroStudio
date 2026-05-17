package com.itsaky.androidide.templates.impl.chaquopy.base

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import java.io.File

private const val AGP_VERSION = "8.13.0"
private const val KOTLIN_VERSION = "2.2.20"
private const val CHAQUOPY_VERSION = "17.0.0"
private const val COMPILE_SDK = 36
private const val MIN_SDK = 24
private const val TARGET_SDK = 36

enum class ChaquopyUiKind {
  Xml,
  Compose,
  Litho,
}

data class ChaquopyTemplateSpec(
    val uiKind: ChaquopyUiKind,
    val pythonModuleName: String,
    val pythonMessageArgument: String,
)

internal fun AndroidModuleTemplateBuilder.writeChaquopyTemplate(spec: ChaquopyTemplateSpec) {
  writeChaquopyBuildFiles(spec.uiKind)
  writeChaquopyPythonSource(spec)
}

private fun AndroidModuleTemplateBuilder.writeChaquopyBuildFiles(uiKind: ChaquopyUiKind) {
  val appDir = File(data.projectDir, "app")
  val rootGradleFile = File(data.projectDir, if (data.useKts) "build.gradle.kts" else "build.gradle")
  val appGradleFile = File(appDir, if (data.useKts) "build.gradle.kts" else "build.gradle")

  executor.save(rootGradle(data.useKts), rootGradleFile)
  executor.save(appGradle(data.useKts, data.packageName, uiKind), appGradleFile)

  if (data.useToml) {
    executor.save(versionCatalog(), File(data.projectDir, "gradle/libs.versions.toml"))
  }
}

private fun AndroidModuleTemplateBuilder.writeChaquopyPythonSource(spec: ChaquopyTemplateSpec) {
  val pythonDir = File(data.projectDir, "app/src/main/python")
  val moduleFile = File(pythonDir, "${spec.pythonModuleName}.py")
  executor.save(pythonModule(spec.pythonMessageArgument), moduleFile)
}

private fun rootGradle(kts: Boolean) =
    if (kts) {
      """
plugins {
  id("com.android.application") version "$AGP_VERSION" apply false
  id("org.jetbrains.kotlin.android") version "$KOTLIN_VERSION" apply false
  id("com.chaquo.python") version "$CHAQUOPY_VERSION" apply false
}
""".trim()
    } else {
      """
plugins {
  id 'com.android.application' version '$AGP_VERSION' apply false
  id 'org.jetbrains.kotlin.android' version '$KOTLIN_VERSION' apply false
  id 'com.chaquo.python' version '$CHAQUOPY_VERSION' apply false
}
""".trim()
    }

private fun appGradle(kts: Boolean, pkg: String, uiKind: ChaquopyUiKind): String {
  val (extraAndroid, dependencies) = uiConfig(uiKind, kts)
  return if (kts) {
    """
plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.chaquo.python")
}

android {
  namespace = "$pkg"
  compileSdk = $COMPILE_SDK

  defaultConfig {
    applicationId = "$pkg"
    minSdk = $MIN_SDK
    targetSdk = $TARGET_SDK
    versionCode = 1
    versionName = "1.0"
  }

$extraAndroid
}

chaquopy {
  defaultConfig {
    pip {
      install("six")
    }
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("com.google.android.material:material:1.12.0")
$dependencies
}
""".trim()
  } else {
    """
plugins {
  id 'com.android.application'
  id 'org.jetbrains.kotlin.android'
  id 'com.chaquo.python'
}

android {
  namespace '$pkg'
  compileSdk $COMPILE_SDK

  defaultConfig {
    applicationId "$pkg"
    minSdk $MIN_SDK
    targetSdk $TARGET_SDK
    versionCode 1
    versionName "1.0"
  }

$extraAndroid
}

chaquopy {
  defaultConfig {
    pip {
      install "six"
    }
  }
}

dependencies {
  implementation 'androidx.core:core-ktx:1.13.0'
  implementation 'androidx.appcompat:appcompat:1.7.1'
  implementation 'com.google.android.material:material:1.12.0'
$dependencies
}
""".trim()
  }
}

private fun uiConfig(kind: ChaquopyUiKind, kts: Boolean): Pair<String, String> =
    when (kind) {
      ChaquopyUiKind.Xml -> "" to ""
      ChaquopyUiKind.Compose -> {
        val androidBlock = if (kts) "  buildFeatures { compose = true }" else "  buildFeatures { compose true }"
        val deps =
            if (kts) {
              "  implementation(\"androidx.activity:activity-compose:1.10.1\")\n" +
                  "  implementation(\"androidx.compose.material3:material3:1.3.2\")"
            } else {
              "  implementation 'androidx.activity:activity-compose:1.10.1'\n" +
                  "  implementation 'androidx.compose.material3:material3:1.3.2'"
            }
        androidBlock to deps
      }
      ChaquopyUiKind.Litho -> {
        val deps =
            if (kts) {
              "  implementation(\"com.facebook.litho:litho-core:0.52.0\")\n" +
                  "  implementation(\"com.facebook.litho:litho-widget:0.52.0\")\n" +
                  "  kapt(\"com.facebook.litho:litho-processor:0.52.0\")"
            } else {
              "  implementation 'com.facebook.litho:litho-core:0.52.0'\n" +
                  "  implementation 'com.facebook.litho:litho-widget:0.52.0'\n" +
                  "  kapt 'com.facebook.litho:litho-processor:0.52.0'"
            }
        "" to deps
      }
    }

private fun versionCatalog() =
    """
[versions]
agp = "$AGP_VERSION"
kotlin = "$KOTLIN_VERSION"
chaquopy = "$CHAQUOPY_VERSION"

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
kotlinAndroid = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
chaquopy = { id = "com.chaquo.python", version.ref = "chaquopy" }
""".trim()

private fun pythonModule(messageTarget: String) =
    """def get_message(name=None):
    target = name or "$messageTarget"
    return f"Hello from Chaquopy, {target}!"
""".trim()
