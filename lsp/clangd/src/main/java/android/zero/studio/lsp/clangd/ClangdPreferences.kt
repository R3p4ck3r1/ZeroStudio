package android.zero.studio.lsp.clangd

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.preference.Preference
import com.itsaky.androidide.app.BaseApplication
import com.itsaky.androidide.lsp.clangd.R
import com.itsaky.androidide.preferences.IPreference
import com.itsaky.androidide.preferences.IPreferenceScreen
import com.itsaky.androidide.preferences.PreferenceChoices
import com.itsaky.androidide.preferences.SingleChoicePreference

object ClangdPreferences : IPreferenceScreen() {
  override val key: String = "screen_clangd_preferences"
  override val title: Int = R.string.clangd_preferences_title
  override val children: List<IPreference> = listOf(NdkVersionSelectorPreference)
  override fun onCreateView(context: Context): Preference = super.onCreateView(context).also { it.summary = "Configure clangd and Android NDK toolchain detection" }
  override fun describeContents() = 0
  override fun writeToParcel(dest: Parcel, flags: Int) = Unit
  @JvmField val CREATOR = object : Parcelable.Creator<ClangdPreferences> {
    override fun createFromParcel(source: Parcel?) = ClangdPreferences
    override fun newArray(size: Int) = Array(size) { ClangdPreferences }
  }
}

object NdkVersionSelectorPreference : SingleChoicePreference() {
  override val key: String = ClangdServerSettings.KEY_TARGET_NDK_VERSION
  override val title: Int = R.string.clangd_ndk_version_title
  override fun getEntries(preference: Preference): Array<PreferenceChoices.Entry> {
    val selected = BaseApplication.getBaseInstance()?.prefManager?.getString(key, "").orEmpty()
    val versions = listOf("" to "Auto-detect") + NdkToolchainLocator.installedNdks().map { it.version to if (it.usable) it.version else "${it.version} (${it.problem})" }
    return versions.map { (value, label) -> PreferenceChoices.Entry(label, selected == value || (selected.isBlank() && value.isBlank()), value) }.toTypedArray()
  }
  override fun onChoiceConfirmed(preference: Preference, entry: PreferenceChoices.Entry?, position: Int) {
    val value = entry?.data as? String ?: ""
    BaseApplication.getBaseInstance()?.prefManager?.putString(key, value)
    preference.summary = if (value.isBlank()) "Auto-detect" else value
  }
  override fun describeContents() = 0
  override fun writeToParcel(dest: Parcel, flags: Int) = Unit
  @JvmField val CREATOR = object : Parcelable.Creator<NdkVersionSelectorPreference> {
    override fun createFromParcel(source: Parcel?) = NdkVersionSelectorPreference
    override fun newArray(size: Int) = Array(size) { NdkVersionSelectorPreference }
  }
}
