/*
 * This file is part of AndroidIDE.
 *
 * AndroidIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidIDE. If not, see <https://www.gnu.org/licenses/>.
 */
package com.itsaky.androidide.builder.model

import com.android.builder.model.v2.dsl.ClassField
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.dsl.SigningConfig
import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.ide.VectorDrawablesOptions
import java.io.File
import java.io.Serializable

/**
 * Default implementation of ClassField for AGP v2
 *
 * @author android_zero
 */
data class DefaultClassField(
    override val type: String,
    override val name: String,
    override val value: String,
    override val documentation: String,
    override val annotations: Set<String>
) : ClassField, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromClassField(classField: ClassField): DefaultClassField {
            return DefaultClassField(
                type = classField.type,
                name = classField.name,
                value = classField.value,
                documentation = classField.documentation,
                annotations = classField.annotations
            )
        }
    }
}

/**
 * Default implementation of VectorDrawablesOptions for AGP v2
 *
 * @author android_zero
 */
data class DefaultVectorDrawablesOptions(
    override val generatedDensities: Set<String>?,
    override val useSupportLibrary: Boolean?
) : VectorDrawablesOptions, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromVectorDrawablesOptions(options: VectorDrawablesOptions): DefaultVectorDrawablesOptions {
            return DefaultVectorDrawablesOptions(
                generatedDensities = options.generatedDensities,
                useSupportLibrary = options.useSupportLibrary
            )
        }
    }
}

/**
 * Default implementation of SigningConfig for AGP v2
 *
 * @author android_zero
 */
class DefaultSigningConfig : SigningConfig, Serializable {
    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromSigningConfig(signingConfig: SigningConfig): DefaultSigningConfig {
            return DefaultSigningConfig().apply {
                name = signingConfig.name
                storeFile = signingConfig.storeFile
                storePassword = signingConfig.storePassword
                keyAlias = signingConfig.keyAlias
                keyPassword = signingConfig.keyPassword
                enableV1Signing = signingConfig.enableV1Signing
                enableV2Signing = signingConfig.enableV2Signing
                enableV3Signing = signingConfig.enableV3Signing
                enableV4Signing = signingConfig.enableV4Signing
            }
        }
    }

    override var name: String = ""
    override var storeFile: File? = null
    override var storePassword: String? = null
    override var keyAlias: String? = null
    override var keyPassword: String? = null
    override var enableV1Signing: Boolean? = null
    override var enableV2Signing: Boolean? = null
    override var enableV3Signing: Boolean? = null
    override var enableV4Signing: Boolean? = null

    override val isSigningReady: Boolean
        get() {
            return name.isNotEmpty() &&
                storeFile != null &&
                !storePassword.isNullOrEmpty() &&
                !keyAlias.isNullOrEmpty() &&
                !keyPassword.isNullOrEmpty()
        }
}

/**
 * Default implementation of BaseConfig common fields
 *
 * @author android_zero
 */
abstract class DefaultBaseConfig : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    open var name: String = ""
    open var applicationIdSuffix: String? = null
    open var versionNameSuffix: String? = null
    open var buildConfigFields: Map<String, DefaultClassField>? = null
    open var resValues: Map<String, DefaultClassField>? = null
    open var proguardFiles: Collection<File> = emptyList()
    open var consumerProguardFiles: Collection<File> = emptyList()
    open var testProguardFiles: Collection<File> = emptyList()
    open var manifestPlaceholders: Map<String, Any> = emptyMap()
    open var multiDexEnabled: Boolean? = null
    open var multiDexKeepFile: File? = null
    open var multiDexKeepProguard: File? = null
    open var isDefault: Boolean? = null
}

/**
 * Default implementation of BuildType for AGP v2
 *
 * @author android_zero
 */
class DefaultBuildType : DefaultBaseConfig(), BuildType, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromBuildType(buildType: BuildType): DefaultBuildType {
            return DefaultBuildType().apply {
                name = buildType.name
                applicationIdSuffix = buildType.applicationIdSuffix
                versionNameSuffix = buildType.versionNameSuffix
                buildConfigFields = buildType.buildConfigFields?.mapValues { (_, field) ->
                    DefaultClassField.fromClassField(field)
                }
                resValues = buildType.resValues?.mapValues { (_, field) ->
                    DefaultClassField.fromClassField(field)
                }
                proguardFiles = buildType.proguardFiles
                consumerProguardFiles = buildType.consumerProguardFiles
                testProguardFiles = buildType.testProguardFiles
                manifestPlaceholders = buildType.manifestPlaceholders
                multiDexEnabled = buildType.multiDexEnabled
                multiDexKeepFile = buildType.multiDexKeepFile
                multiDexKeepProguard = buildType.multiDexKeepProguard
                isDefault = buildType.isDefault
                isDebuggable = buildType.isDebuggable
                isProfileable = buildType.isProfileable
                isTestCoverageEnabled = buildType.isTestCoverageEnabled
                isPseudoLocalesEnabled = buildType.isPseudoLocalesEnabled
                isJniDebuggable = buildType.isJniDebuggable
                isRenderscriptDebuggable = buildType.isRenderscriptDebuggable
                matchingFallbacks = buildType.matchingFallbacks
                renderscriptOptimLevel = buildType.renderscriptOptimLevel
                isMinifyEnabled = buildType.isMinifyEnabled
                isZipAlignEnabled = buildType.isZipAlignEnabled
                isEmbedMicroApp = buildType.isEmbedMicroApp
                signingConfig = buildType.signingConfig
                isShrinkResources = buildType.isShrinkResources
            }
        }
    }

    override var isDebuggable: Boolean = false
    override var isProfileable: Boolean = false
    override var isTestCoverageEnabled: Boolean = false
    override var isPseudoLocalesEnabled: Boolean = false
    override var isJniDebuggable: Boolean = false
    override var isRenderscriptDebuggable: Boolean = false
    override var matchingFallbacks: List<String> = emptyList()
    override var renderscriptOptimLevel: Int = 0
    override var isMinifyEnabled: Boolean = false
    override var isZipAlignEnabled: Boolean = true
    override var isEmbedMicroApp: Boolean = true
    override var signingConfig: String? = null
    override var isShrinkResources: Boolean = false
}

/**
 * Default implementation of ProductFlavor for AGP v2
 *
 * @author android_zero
 */
class DefaultProductFlavor : DefaultBaseConfig(), ProductFlavor, Serializable {

    companion object {
        private const val serialVersionUID = 1L

        @JvmStatic
        fun fromProductFlavor(productFlavor: ProductFlavor): DefaultProductFlavor {
            return DefaultProductFlavor().apply {
                name = productFlavor.name
                applicationIdSuffix = productFlavor.applicationIdSuffix
                versionNameSuffix = productFlavor.versionNameSuffix
                buildConfigFields = productFlavor.buildConfigFields?.mapValues { (_, field) ->
                    DefaultClassField.fromClassField(field)
                }
                resValues = productFlavor.resValues?.mapValues { (_, field) ->
                    DefaultClassField.fromClassField(field)
                }
                proguardFiles = productFlavor.proguardFiles
                consumerProguardFiles = productFlavor.consumerProguardFiles
                testProguardFiles = productFlavor.testProguardFiles
                manifestPlaceholders = productFlavor.manifestPlaceholders
                multiDexEnabled = productFlavor.multiDexEnabled
                multiDexKeepFile = productFlavor.multiDexKeepFile
                multiDexKeepProguard = productFlavor.multiDexKeepProguard
                isDefault = productFlavor.isDefault
                dimension = productFlavor.dimension
                applicationId = productFlavor.applicationId
                versionCode = productFlavor.versionCode
                versionName = productFlavor.versionName
                minSdkVersion = productFlavor.minSdkVersion?.let { DefaultApiVersion.fromApiVersion(it) }
                targetSdkVersion = productFlavor.targetSdkVersion?.let { DefaultApiVersion.fromApiVersion(it) }
                matchingFallbacks = productFlavor.matchingFallbacks
                missingDimensionStrategy = productFlavor.missingDimensionStrategy
                maxSdkVersion = productFlavor.maxSdkVersion
                renderscriptTargetApi = productFlavor.renderscriptTargetApi
                renderscriptSupportModeEnabled = productFlavor.renderscriptSupportModeEnabled
                renderscriptSupportModeBlasEnabled = productFlavor.renderscriptSupportModeBlasEnabled
                renderscriptNdkModeEnabled = productFlavor.renderscriptNdkModeEnabled
                testApplicationId = productFlavor.testApplicationId
                testInstrumentationRunner = productFlavor.testInstrumentationRunner
                testInstrumentationRunnerArguments = productFlavor.testInstrumentationRunnerArguments
                testHandleProfiling = productFlavor.testHandleProfiling
                testFunctionalTest = productFlavor.testFunctionalTest
                resourceConfigurations = productFlavor.resourceConfigurations
                signingConfig = productFlavor.signingConfig
                vectorDrawables = DefaultVectorDrawablesOptions.fromVectorDrawablesOptions(productFlavor.vectorDrawables)
                wearAppUnbundled = productFlavor.wearAppUnbundled
            }
        }
    }

    override var dimension: String? = null
    override var applicationId: String? = null
    override var versionCode: Int? = null
    override var versionName: String? = null
    override var minSdkVersion: DefaultApiVersion? = null
    override var targetSdkVersion: DefaultApiVersion? = null
    override var matchingFallbacks: List<String> = emptyList()
    override var missingDimensionStrategy: Map<String, List<String>> = emptyMap()
    override var maxSdkVersion: Int? = null
    override var renderscriptTargetApi: Int? = null
    override var renderscriptSupportModeEnabled: Boolean? = null
    override var renderscriptSupportModeBlasEnabled: Boolean? = null
    override var renderscriptNdkModeEnabled: Boolean? = null
    override var testApplicationId: String? = null
    override var testInstrumentationRunner: String? = null
    override var testInstrumentationRunnerArguments: Map<String, String> = emptyMap()
    override var testHandleProfiling: Boolean? = null
    override var testFunctionalTest: Boolean? = null
    override var resourceConfigurations: Collection<String> = emptyList()
    override var signingConfig: String? = null
    override var vectorDrawables: DefaultVectorDrawablesOptions = DefaultVectorDrawablesOptions(null, null)
    override var wearAppUnbundled: Boolean? = null
}
