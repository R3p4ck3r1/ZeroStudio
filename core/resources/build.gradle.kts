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

import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.tasks.ZeroAutoTranslateTask

plugins { id("com.android.library") }

android { namespace = "${BuildConfig.packageName}.resources"

 }

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.splashscreen)
  implementation(libs.google.material)
  api(libs.androidx.nav.ui)
  api(libs.androidx.nav.fragment)
}

// 运行 ./gradlew translateStrings 来执行翻译
// @author android_zero  github：android-zeros
tasks.register<ZeroAutoTranslateTask>("translateStrings") {
  // 添加翻译的源文件 (使用逗号隔开)
  sourceXmlPaths = "core/resources/src/main/res/values/dev_test_res.xml"
  // sourceXmlPaths = "core/resources/src/main/res/values/strings.xml,
  // core/resources/src/main/res/values/dev_test_res.xml,
  // core/resources/src/main/res/values/strings_git.xml,
  // core/resources/src/main/res/values/termux_app_strings.xml,
  // core/resources/src/main/res/values/termux_shared_strings.xml"

  // 添加翻译引擎 (轮询负载均衡，提高稳定性和速度)
  translationEngines = "GOOGLE_GTX, GOOGLE_WEB"

  // 设置 DeepSeek 密钥 (请替换为您自己的有效密钥)
  deepSeekApiKey = ""

  concurrency = 2

  // 设置输出和备份路径
  translationOutputDirName = "StringTranslation"
  originalFileBackupDirName = "StringTranslation/backup"
}
