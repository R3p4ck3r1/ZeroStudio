/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.plugins.conf

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.BaseExtension
import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.build.config.FDroidConfig
import com.itsaky.androidide.build.config.isFDroidBuild
import com.itsaky.androidide.build.config.projectVersionCode
import com.itsaky.androidide.build.config.simpleVersionName
import com.itsaky.androidide.plugins.NoDesugarPlugin
import com.itsaky.androidide.plugins.util.SdkUtils.getAndroidJar
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * ABIs for which the product flavors will be created. The keys in this map are the names of the
 * product flavors whereas, the value for each flavor is a number that identifies the ABI in the
 * previous per-ABI versionCode encoding.
 *
 * Note: per-ABI versionCode offset has been removed. The new versionCode is
 * `YYYYMMDDNN` (shared across all ABIs of the same build), so the value here is purely
 * informational and kept only for the [flavorsAbis] consumer (`splits { abi { ... } }`).
 */
internal val flavorsAbis = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

fun Project.configureAndroidModule(coreLibDesugDep: Provider<MinimalExternalModuleDependency>) {
  val isAppModule = plugins.hasPlugin("com.android.application")
  assert(isAppModule || plugins.hasPlugin("com.android.library")) {
    "${javaClass.simpleName} can only be applied to Android projects"
  }

  val androidJar =
      extensions
          .getByType(AndroidComponentsExtension::class.java)
          .getAndroidJar(assertExists = true)
  val frameworkStubsJar =
      findProject(":utilities:framework-stubs")!!.file("libs/android.jar").also {
        it.parentFile.mkdirs()
      }

  if (!(frameworkStubsJar.exists() && frameworkStubsJar.isFile)) {
    androidJar.copyTo(frameworkStubsJar)
  }

  tasks.withType(KotlinCompile::class.java).configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
  }

  extensions.getByType(CommonExtension::class.java).run {
    lint { checkDependencies = true }

    packaging {
      resources {
        excludes.addAll(
            arrayOf(
                "META-INF/CHANGES",
                "META-INF/README.md",
            )
        )
        pickFirsts.addAll(
            arrayOf(
                "META-INF/eclipse.inf",
                "META-INF/LICENSE.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/INDEX.LIST",
                "about_files/LICENSE-2.0.txt",
                "plugin.xml",
                "plugin.properties",
                "about.mappings",
                "about.properties",
                "about.ini",
                "modeling32.png",
            )
        )
      }
    }
  }

  extensions.getByType(BaseExtension::class.java).run {
    compileSdkVersion(BuildConfig.compileSdk)

    defaultConfig {
      minSdk = BuildConfig.minSdk
      targetSdk = BuildConfig.targetSdk
      // versionCode = YYYYMMDDNN, versionName = vYYYYMMDD-NN-sha7
      // 两者均来自 ProjectConfig, 由 -Pide.build.dailyCounter / -Pide.build.gitShortSha 注入.
      versionCode = projectVersionCode
      versionName = simpleVersionName

      // required
      multiDexEnabled = true

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
      sourceCompatibility = BuildConfig.javaVersion17
      targetCompatibility = BuildConfig.javaVersion17
    }

    configureCoreLibDesugaring(coreLibDesugDep)

    if (project.plugins.hasPlugin("com.itsaky.androidide.core-app")) {
      packagingOptions { jniLibs { useLegacyPackaging = true } }

      splits {
        abi {
          reset()
          isEnable = true
          isUniversalApk = false
          if (isFDroidBuild) {
            include(FDroidConfig.fDroidBuildArch!!)
          } else {
            include(*flavorsAbis.keys.toTypedArray())
          }
        }
      }
      // 注: 不再为每个 ABI 单独设置 versionCode; 所有 ABI 共享同一 versionCode (YYYYMMDDNN),
      // 与 versionName 中的 NN 保持一致. 这也避免了 100*N + ABI 编码在 10 位 versionCode
      // (上限 2,100,000,000) 下溢出的问题.
    } else {
      defaultConfig {
        ndk {
          abiFilters.clear()
          abiFilters += flavorsAbis.keys
        }
      }
    }

    buildTypes.getByName("debug") { isMinifyEnabled = false }
    buildTypes.getByName("release") {

      // from AGP 8.4.0 onwards, there are some behavioral changes in R8
      // enabling R8 on library projects results in missing class errors
      // see https://issuetracker.google.com/issues/338411137#comment11
      isMinifyEnabled = isAppModule
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    // development build type
    // similar to 'release', but disables proguard/r8
    // this build type can be used to gain release-like performance at runtime
    // the build are faster for this build type as compared to 'release'
    // buildTypes.register("dev") {
    // initWith(buildTypes.getByName("release"))
    // isMinifyEnabled = false
    // }

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures.viewBinding = true
    buildFeatures.buildConfig = true
    // buildFeatures.compose = true
  }
}

private fun Project.configureCoreLibDesugaring(
    coreLibDesugDep: Provider<MinimalExternalModuleDependency>
) {
  if (!plugins.hasPlugin(NoDesugarPlugin::class.java)) {
    dependencies.add("coreLibraryDesugaring", coreLibDesugDep)
  }
}

// private fun Project.configureCoreLibDesugaring(
  // baseExtension: BaseExtension,
  // coreLibDesugDep: Provider<MinimalExternalModuleDependency>
// ) {
  // val coreLibDesugaringEnabled = !project.plugins.hasPlugin(NoDesugarPlugin::class.java)

  // // baseExtension.compileOptions.isCoreLibraryDesugaringEnabled = coreLibDesugaringEnabled

  // if (coreLibDesugaringEnabled) {
    // project.dependencies.add("coreLibraryDesugaring", coreLibDesugDep)
  // }
// }
