package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.models.ndk.NativeAbi
import com.android.builder.model.v2.models.ndk.NativeVariant
import com.android.builder.model.v2.models.ndk.NativeModule
import com.android.builder.model.v2.models.ndk.NativeBuildSystem
import java.io.File
import java.io.Serializable

data class DefaultNativeAbi(
    override val name: String,
    override val sourceFlagsFile: File,
    override val symbolFolderIndexFile: File,
    override val buildFileIndexFile: File,
    override val additionalProjectFilesIndexFile: File
) : NativeAbi, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromNativeAbi(nativeAbi: NativeAbi): DefaultNativeAbi {
            return DefaultNativeAbi(
                name = nativeAbi.name,
                sourceFlagsFile = nativeAbi.sourceFlagsFile,
                symbolFolderIndexFile = nativeAbi.symbolFolderIndexFile,
                buildFileIndexFile = nativeAbi.buildFileIndexFile,
                additionalProjectFilesIndexFile = nativeAbi.additionalProjectFilesIndexFile
            )
        }
    }
}

data class DefaultNativeVariant(
    override val name: String,
    override val abis: List<DefaultNativeAbi>
) : NativeVariant, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromNativeVariant(nativeVariant: NativeVariant): DefaultNativeVariant {
            return DefaultNativeVariant(
                name = nativeVariant.name,
                abis = nativeVariant.abis.map { DefaultNativeAbi.fromNativeAbi(it) }
            )
        }
    }
}

data class DefaultNativeModule(
    override val name: String,
    override val nativeBuildSystem: NativeBuildSystem,
    override val ndkVersion: String,
    override val defaultNdkVersion: String,
    override val externalNativeBuildFile: File,
    override val variants: List<DefaultNativeVariant>
) : NativeModule, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromNativeModule(nativeModule: NativeModule): DefaultNativeModule {
            return DefaultNativeModule(
                name = nativeModule.name,
                nativeBuildSystem = nativeModule.nativeBuildSystem,
                ndkVersion = nativeModule.ndkVersion,
                defaultNdkVersion = nativeModule.defaultNdkVersion,
                externalNativeBuildFile = nativeModule.externalNativeBuildFile,
                variants = nativeModule.variants.map { DefaultNativeVariant.fromNativeVariant(it) }
            )
        }
    }
}
