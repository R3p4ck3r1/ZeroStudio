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

package android.zero.studio.lsp.clangd

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.preference.Preference
import com.itsaky.androidide.lsp.clangd.R
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.preferences.IPreference
import com.itsaky.androidide.preferences.IPreferenceScreen
import com.itsaky.androidide.preferences.PreferenceChoices
import com.itsaky.androidide.preferences.SingleChoicePreference

/**
 * 在设置页面中添加 NDK 探测版本，提供下拉列表让用户切换 NDK 版本。
 * 包含 Clangd LSP 的综合偏好设置视图。
 *
 * @author android_zero
 */
object ClangdPreferences : IPreferenceScreen() {

    override val key: String = "screen_clangd_preferences"
    
    override val title: Int = R.string.clangd_preferences_title

    override val children: List<IPreference> = listOf(
        NdkVersionSelectorPreference
    )

    override fun onCreateView(context: Context): Preference {
        val pref = super.onCreateView(context)
        pref.title = context.getString(title)
        pref.summary = "Configure NDK toolchain and Clangd behavior"
        return pref
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {}

    @JvmField
    val CREATOR =
        object : Parcelable.Creator<ClangdPreferences> {
            override fun createFromParcel(source: Parcel?): ClangdPreferences = ClangdPreferences

            override fun newArray(size: Int): Array<ClangdPreferences> = Array(size) { ClangdPreferences }
        }
}

/**
 * NDK 版本单选配置项。
 * 动态读取本机已下载的 NDK 目录，允许用户切换使用的 Clangd 环境。
 */
object NdkVersionSelectorPreference : SingleChoicePreference() {
    
    override val key: String = ClangdServerSettings.KEY_TARGET_NDK_VERSION
    override val title: Int = R.string.clangd_ndk_version_title

    override fun onCreateView(context: Context): Preference {
        val pref = super.onCreateView(context)
        pref.title = context.getString(title)
        
        val currentVersion = BaseApplication.getBaseInstance().prefManager.getString(key, "")
        pref.summary = if (currentVersion.isNullOrBlank()) {
            "Auto-detect (Latest)"
        } else {
            "Selected: $currentVersion"
        }
        return pref
    }

    override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
        val versions = mutableListOf("Auto-detect")
        versions.addAll(NdkToolchainLocator.getAvailableNdkVersions())
        
        val current = BaseApplication.getBaseInstance().prefManager.getString(key, "")

        return versions.map { version ->
            val actualData = if (version == "Auto-detect") "" else version
            val isChecked = (current == actualData) || (current.isNullOrBlank() && version == "Auto-detect")
            
            PreferenceChoices.Entry(
                label = version,
                _isChecked = isChecked,
                data = actualData
            )
        }.toTypedArray()
    }

    override fun onChoiceConfirmed(preference: Preference, entry: PreferenceChoices.Entry?, position: Int) {
        val selectedVersion = entry?.data as? String ?: return
        BaseApplication.getBaseInstance().prefManager.putString(key, selectedVersion)
        
        // 更新 UI 上的 summary
        preference.summary = if (selectedVersion.isBlank()) {
            "Auto-detect (Latest)"
        } else {
            "Selected: $selectedVersion"
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {}

    @JvmField
    val CREATOR =
        object : Parcelable.Creator<NdkVersionSelectorPreference> {
            override fun createFromParcel(source: Parcel?): NdkVersionSelectorPreference = NdkVersionSelectorPreference

            override fun newArray(size: Int): Array<NdkVersionSelectorPreference> = Array(size) { NdkVersionSelectorPreference }
        }
}
