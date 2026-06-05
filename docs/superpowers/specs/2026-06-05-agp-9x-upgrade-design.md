# AndroidIDE Tooling API AGP 9.x 升级设计文档

**版本**: 1.0  
**日期**: 2026-06-05  
**状态**: 草稿

---

## 一、概述

### 1.1 目标

全面升级AndroidIDE的Gradle Tooling构建服务，实现：
- AGP 7.x-9.x 全版本兼容
- AGP 9.x KTS DSL 语法支持
- 非安卓Gradle项目支持（SpringBoot、Kotlin JVM、Java）
- 完整的桌面级IDE构建功能

### 1.2 当前问题

```
05-06 16:53:17.083 ERROR [pool-13-thread-1] ToolingApiServerImpl: Failed to initialize project
05-06 16:53:17.087 ERROR [ForkJoinPool.commonPool-worker-3] BaseEditorActivity: An error occurred initializing the project with Tooling API
```

**根本原因**：
1. AGP 9.x 模型接口与现有代码不兼容
2. `Versions` 接口在 AGP 9.x 有重大变化
3. KTS DSL 解析需要更新

---

## 二、架构分析

### 2.1 现有模块结构

```
tooling/
├── api/                 # API接口定义
│   ├── IToolingApiServer.kt
│   ├── IToolingApiClient.kt
│   ├── IProject.kt
│   ├── IAndroidProject.kt
│   ├── IGradleProject.kt
│   └── IJavaProject.kt
│
├── model/              # 数据模型
│   └── models/
│       ├── GradleBuildEnvironment.kt
│       ├── GradleBuildMetadata.kt
│       └── ...
│
├── builder-model-impl/ # AGP模型实现
│   └── src/main/java/
│       └── com/itsaky/androidide/builder/model/
│           ├── DefaultVariant.kt
│           ├── DefaultAndroidArtifact.kt
│           └── ...
│
├── impl/               # Tooling API实现
│   └── src/main/java/
│       └── com/itsaky/androidide/tooling/impl/
│           ├── ToolingApiServerImpl.kt
│           ├── sync/
│           │   ├── RootModelBuilder.kt
│           │   ├── AndroidProjectModelBuilder.kt
│           │   └── GradleProjectModelBuilder.kt
│           └── internal/
│               ├── AndroidProjectImpl.kt
│               └── GradleProjectImpl.kt
│
└── events/             # 事件模型
```

### 2.2 AGP模型演进

| AGP版本 | 模型接口 | 主要变化 |
|---------|---------|---------|
| 7.x | v1 | 基础模型 |
| 8.0 | v2 | 全新模型架构 |
| 8.1-8.7 | v2.1 | 增量更新 |
| 9.0 | v3 | KTS优先、namespace强制 |
| 9.1-9.3 | v3.1 | 新增Versions接口 |

---

## 三、详细设计

### 3.1 新增模块结构

```
tooling/
├── api/
│   └── extension/           # 新增：扩展接口
│       ├── IAgpProject.kt   # AGP特定项目接口
│       ├── ISpringBootProject.kt
│       ├── IKotlinJvmProject.kt
│       └── IJavaProjectExtension.kt
│
├── builder-model-impl/
│   └── src/main/java/
│       └── com/itsaky/androidide/builder/model/
│           ├── agp/         # 新增：AGP版本兼容层
│           │   ├── AgpVersion.kt
│           │   ├── AgpVersionChecker.kt
│           │   ├── V7ModelAdapter.kt
│           │   ├── V8ModelAdapter.kt
│           │   └── V9ModelAdapter.kt
│           └── dto/         # 新增：DTO模型
│               ├── AgpVersionsDto.kt
│               ├── AndroidDslDto.kt
│               └── ProjectSyncIssuesDto.kt
│
└── impl/
    └── src/main/java/
        └── com/itsaky/androidide/tooling/impl/
            ├── ProjectTypeDetector.kt    # 新增：项目类型检测
            ├── GradleProjectRegistry.kt  # 新增：项目类型注册
            ├── sync/
            │   ├── AndroidProjectModelBuilder.kt  # 扩展
            │   ├── GradleProjectModelBuilder.kt   # 扩展
            │   ├── NonAndroidProjectBuilder.kt     # 新增
            │   ├── SpringBootProjectBuilder.kt    # 新增
            │   └── KotlinJvmProjectBuilder.kt      # 新增
            └── internal/
                ├── AndroidProjectImpl.kt  # 扩展
                └── GradleProjectImpl.kt   # 扩展
```

### 3.2 核心接口定义

#### 3.2.1 AGP版本兼容性接口

```kotlin
// com.itsaky.androidide.tooling.api.extension

/**
 * AGP版本信息接口
 */
interface IAgpVersion {
    val major: Int
    val minor: Int
    val patch: Int
    val isPreview: Boolean
    val versionCode: String
}

/**
 * AGP版本检测器
 */
interface IAgpVersionDetector {
    fun detect(gradleProject: GradleProject): IAgpVersion
    fun isSupported(version: IAgpVersion): Boolean
    fun getSupportedVersions(): List<IAgpVersion>
}

/**
 * AGP版本兼容适配器
 */
interface IAgpModelAdapter {
    val supportedVersion: IAgpVersion
    
    fun adaptBasicAndroidProject(
        raw: Any, 
        version: IAgpVersion
    ): BasicAndroidProject
    
    fun adaptAndroidProject(
        raw: Any,
        version: IAgpVersion
    ): AndroidProject
    
    fun adaptVariantDependencies(
        raw: Any,
        version: IAgpVersion
    ): VariantDependencies
    
    fun adaptVersions(
        raw: Any,
        version: IAgpVersion
    ): Versions
}
```

#### 3.2.2 非安卓Gradle项目接口

```kotlin
// com.itsaky.androidide.tooling.api.extension

/**
 * Gradle项目类型枚举
 */
enum class GradleProjectCategory {
    ANDROID_APP,
    ANDROID_LIBRARY,
    ANDROID_FEATURE,
    ANDROID_DYNAMIC_FEATURE,
    SPRING_BOOT_APPLICATION,
    SPRING_BOOT_LIBRARY,
    KOTLIN_JVM_APPLICATION,
    KOTLIN_JVM_LIBRARY,
    JAVA_APPLICATION,
    JAVA_LIBRARY,
    GRADLE_PLUGIN,
    UNKNOWN
}

/**
 * Gradle项目构建器接口
 */
interface IGradleProjectBuilder {
    val supportedCategory: GradleProjectCategory
    
    fun canBuild(gradleProject: GradleProject): Boolean
    
    fun build(
        gradleProject: GradleProject,
        buildEnvironment: BuildEnvironment?,
        gradleBuild: GradleBuild?
    ): IGradleProject
}

/**
 * SpringBoot项目接口
 */
interface ISpringBootProject : IGradleProject {
    fun getSpringBootVersion(): CompletableFuture<String?>
    fun getMainClass(): CompletableFuture<String?>
    fun getEmbeddedContainerConfig(): CompletableFuture<EmbeddedContainerConfig?>
    fun getBuildpackConfig(): CompletableFuture<BuildpackConfig?>
}

/**
 * Kotlin JVM项目接口
 */
interface IKotlinJvmProject : IGradleProject {
    fun getKotlinVersion(): CompletableFuture<String?>
    fun getJvmTarget(): CompletableFuture<String?>
    fun getCompilerOptions(): CompletableFuture<KotlinCompilerOptions?>
}
```

### 3.3 数据传输对象(DTO)

#### 3.3.1 AGP版本DTO

```kotlin
// com.itsaky.androidide.builder.model.dto

/**
 * AGP版本信息
 */
data class AgpVersionDto(
    val version: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isPreview: Boolean,
    val buildTimestamp: String?
) : Serializable

/**
 * 模型版本信息
 */
data class ModelVersionsDto(
    val agp: AgpVersionDto,
    val modelConsumerMinVersion: Int,
    val modelProducerVersion: Int,
    val basicAndroidProject: Int,
    val androidProject: Int,
    val androidDsl: Int,
    val variantDependencies: Int
) : Serializable
```

#### 3.3.2 非安卓项目DTO

```kotlin
// com.itsaky.androidide.builder.model.dto

/**
 * SpringBoot项目元数据
 */
data class SpringBootProjectMetadata(
    val base: ProjectMetadata,
    val springBootVersion: String?,
    val mainClass: String?,
    val dependencies: List<String>,
    val plugins: List<String>
) : Serializable

/**
 * Kotlin JVM项目元数据
 */
data class KotlinJvmProjectMetadata(
    val base: ProjectMetadata,
    val kotlinVersion: String?,
    val jvmTarget: String?,
    val compilerOptions: KotlinCompilerOptions,
    val sourceSets: List<KotlinSourceSet>
) : Serializable

/**
 * Java项目元数据
 */
data class JavaProjectMetadata(
    val base: ProjectMetadata,
    val javaVersion: String?,
    val sourceSets: List<JavaSourceSet>,
    val annotationsProcessing: AnnotationsProcessingConfig?
) : Serializable
```

### 3.4 版本兼容性矩阵

| 功能 | AGP 7.0-7.4 | AGP 8.0-8.7 | AGP 9.0-9.3 |
|------|-------------|-------------|-------------|
| 基础同步 | ✅ | ✅ | ✅ |
| 变体列表 | ✅ | ✅ | ✅ |
| 依赖解析 | ✅ | ✅ | ✅ |
| KTS DSL | ⚠️ | ✅ | ✅ |
| namespace | N/A | ✅ | ✅ |
| versions接口 | N/A | N/A | ✅ |
| lint检查 | ✅ | ✅ | ✅ |
| AAPT2 | ✅ | ✅ | ✅ |

### 3.5 项目类型检测流程

```
ProjectConnection
     │
     ▼
┌─────────────────────────────┐
│  1. 获取GradleProject模型    │
└─────────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│  2. 检查build.gradle(.kts)  │
│     - 包含android{}块       │──→ Android Project
│     - 包含springBoot{}块     │──→ SpringBoot Project
│     - 包含kotlin{}块         │──→ Kotlin JVM Project
│     - 包含java{}块           │──→ Java Project
│     - 包含gradleplugin{}块   │──→ Gradle Plugin
│     - 其他                    │──→ Unknown
└─────────────────────────────┘
```

---

## 四、关键实现

### 4.1 AGP版本检测

```kotlin
// com.itsaky.androidide.builder.model.agp

class AgpVersionDetector : IAgpVersionDetector {
    
    private val supportedRanges = listOf(
        VersionRange(7, 0, 0, 7, 4, 99),
        VersionRange(8, 0, 0, 8, 7, 99),
        VersionRange(9, 0, 0, 9, 3, 99)
    )
    
    override fun detect(gradleProject: GradleProject): IAgpVersion {
        // 1. 尝试从Versions模型获取
        val versions = try {
            gradleProject.buildEnvironment?.let {
                // 调用Gradle Tooling API获取Versions
            }
        } catch (e: Exception) {
            null
        }
        
        // 2. 从gradle.properties读取
        val propertiesVersion = readFromGradleProperties(gradleProject)
        
        // 3. 从插件ID版本推断
        val pluginVersion = inferFromPluginId(gradleProject)
        
        return chooseBestVersion(versions, propertiesVersion, pluginVersion)
    }
    
    override fun isSupported(version: IAgpVersion): Boolean {
        return supportedRanges.any { it.contains(version) }
    }
}
```

### 4.2 模型适配器工厂

```kotlin
// com.itsaky.androidide.builder.model.agp

object AgpModelAdapterFactory {
    
    fun createAdapter(version: IAgpVersion): IAgpModelAdapter {
        return when {
            version.major == 7 -> V7ModelAdapter()
            version.major == 8 -> V8ModelAdapter()
            version.major >= 9 -> V9ModelAdapter()
            else -> throw UnsupportedAgpVersionException(version)
        }
    }
}

class V9ModelAdapter : IAgpModelAdapter {
    
    override val supportedVersion: IAgpVersion = AgpVersion(9, 0, 0)
    
    override fun adaptVersions(raw: Any, version: IAgpVersion): Versions {
        // AGP 9.x Versions接口实现
        // 注意MINIMUM_MODEL_CONSUMER和MODEL_PRODUCER常量
    }
    
    override fun adaptAndroidProject(raw: Any, version: IAgpVersion): AndroidProject {
        // AGP 9.x AndroidProject接口实现
        // 注意新增的testFixturesNamespace和androidTestNamespace
    }
}
```

### 4.3 项目类型检测器

```kotlin
// com.itsaky.androidide.tooling.impl

class ProjectTypeDetector {
    
    private val builders = listOf(
        SpringBootProjectBuilder(),
        KotlinJvmProjectBuilder(),
        JavaProjectBuilder(),
        GradlePluginProjectBuilder()
    )
    
    fun detect(connection: ProjectConnection): GradleProjectCategory {
        val gradleProject = connection.getModel(GradleProject::class.java)
        
        // 1. 检查是否有AGP插件
        if (hasAndroidPlugin(gradleProject)) {
            return detectAndroidProjectType(gradleProject)
        }
        
        // 2. 检查SpringBoot
        if (hasSpringBootPlugin(gradleProject)) {
            return GradleProjectCategory.SPRING_BOOT_APPLICATION
        }
        
        // 3. 检查Kotlin
        if (hasKotlinPlugin(gradleProject)) {
            return GradleProjectCategory.KOTLIN_JVM_APPLICATION
        }
        
        // 4. 检查Java
        if (hasJavaPlugin(gradleProject)) {
            return GradleProjectCategory.JAVA_APPLICATION
        }
        
        // 5. 检查Gradle插件
        if (hasGradlePluginPlugin(gradleProject)) {
            return GradleProjectCategory.GRADLE_PLUGIN
        }
        
        return GradleProjectCategory.UNKNOWN
    }
    
    private fun hasAndroidPlugin(project: GradleProject): Boolean {
        return project.plugins.any { 
            it.id.startsWith("com.android.") 
        }
    }
    
    private fun hasSpringBootPlugin(project: GradleProject): Boolean {
        return project.plugins.any { 
            it.id == "org.springframework.boot" 
        }
    }
}
```

### 4.4 Gradle项目构建器注册表

```kotlin
// com.itsaky.androidide.tooling.impl

class GradleProjectRegistry {
    
    private val builders = mutableMapOf<GradleProjectCategory, IGradleProjectBuilder>()
    
    fun register(category: GradleProjectCategory, builder: IGradleProjectBuilder) {
        builders[category] = builder
    }
    
    fun getBuilder(category: GradleProjectCategory): IGradleProjectBuilder? {
        return builders[category]
    }
    
    fun getAllBuilders(): List<IGradleProjectBuilder> {
        return builders.values.toList()
    }
    
    companion object {
        val instance = GradleProjectRegistry()
    }
}
```

---

## 五、UI功能设计

### 5.1 设置界面布局

```
Settings
├── Build, Execution, Deployment
│   ├── Build Tools
│   │   ├── Gradle
│   │   │   ├── Gradle Settings
│   │   │   │   ├── Distribution
│   │   │   │   │   ├── Use default Gradle wrapper (推荐)
│   │   │   │   ├── Select specific version
│   │   │   │   │   └── Version: [9.5.1 ▼]
│   │   │   │   └── Use custom Gradle installation
│   │   │   │       └── Path: [________________]
│   │   │   │   
│   │   │   ├── AGP Settings
│   │   │   │   ├── Auto-detect AGP version (推荐)
│   │   │   │   ├── Select specific version
│   │   │   │   │   └── Version: [9.3.0 ▼]
│   │   │   │   └── AGP Version Range: 7.x - 9.x
│   │   │   │
│   │   │   ├── Build Cache
│   │   │   │   ├── ☑ Enable build cache
│   │   │   │   └── Cache location: [________________]
│   │   │   │
│   │   │   ├── Kotlin DSL
│   │   │   │   ├── ☑ Enable KTS script support
│   │   │   │   └── ☑ Enable DSL completion
│   │   │   │
│   │   │   └── Advanced
│   │   │       ├── JVM arguments: [________________]
│   │   │       └── Daemon settings
│   │   │
│   │   └── Android
│   │       ├── SDK Location
│   │       └── NDK Location
│   │
│   └── Debugger
│
└── Languages & Frameworks
    ├── Spring Boot
    │   ├── Spring Boot Settings
    │   └── Enable Spring Boot support
    │
    └── Kotlin
        ├── Kotlin Settings
        └── Enable Kotlin support
```

### 5.2 构建面板UI

```
Build Panel
├── Build Variants (Android)
│   ├── Module: app
│   │   ├── debug
│   │   │   ├── ABI: arm64-v8a
│   │   │   └── API Level: 30
│   │   └── release
│   │       ├── Minify: R8
│   │       └── Sign: ✓
│   │
│   └── Module: library
│       └── debug
│
├── Gradle Tasks
│   ├── :app
│   │   ├── assemble
│   │   ├── assembleDebug
│   │   ├── assembleRelease
│   │   └── build
│   │
│   ├── :library
│   │   ├── assemble
│   │   └── build
│   │
│   └── Build Tasks
│       ├── assembleDebug
│       └── assembleRelease
│
└── Build Output
    ├── > Building...
    ├── > :app:compileDebugKotlin
    └── > ✓ BUILD SUCCESSFUL
```

---

## 六、实施计划

### 阶段1：AGP兼容性修复 (1-2周)

**任务**：
1. 修复序列化问题（已部分完成）
2. 实现 `AgpVersionDetector`
3. 实现 `AgpModelAdapterFactory`
4. 更新 `AndroidProjectModelBuilder` 支持 AGP 9.x
5. 测试 AGP 7.x-9.x 兼容性

**里程碑**：
- ✅ AGP 9.3.0 项目可以正常同步

### 阶段2：非安卓项目支持 (2-3周)

**任务**：
1. 实现 `ProjectTypeDetector`
2. 实现 `SpringBootProjectBuilder`
3. 实现 `KotlinJvmProjectBuilder`
4. 实现 `JavaProjectBuilder`
5. 实现 `GradleProjectRegistry`

**里程碑**：
- ✅ 可以打开和构建 SpringBoot 项目
- ✅ 可以打开和构建 Kotlin JVM 项目
- ✅ 可以打开和构建 Java 项目

### 阶段3：UI功能开发 (2-3周)

**任务**：
1. 实现 Gradle 设置界面
2. 实现 AGP 版本选择 UI
3. 实现构建变体切换 UI
4. 实现 Task 列表和执行 UI

**里程碑**：
- ✅ 完整的构建设置面板
- ✅ 构建变体切换功能
- ✅ Task 列表和执行

### 阶段4：高级功能 (3-4周)

**任务**：
1. 依赖图展示
2. 构建缓存管理
3. 高级 Gradle 属性配置
4. KTS DSL 完整支持

**里程碑**：
- ✅ 完整的桌面级 IDE 构建功能

---

## 七、测试计划

### 7.1 兼容性测试矩阵

| AGP版本 | Gradle版本 | Java版本 | 测试用例数 |
|---------|-----------|---------|-----------|
| 7.0.0 | 7.3 | 11 | 50 |
| 7.4.0 | 7.6 | 11 | 50 |
| 8.0.0 | 8.0 | 17 | 60 |
| 8.2.0 | 8.2 | 17 | 60 |
| 8.5.0 | 8.5 | 17 | 60 |
| 9.0.0 | 8.7 | 17 | 70 |
| 9.3.0 | 8.9 | 17 | 70 |

### 7.2 项目类型测试

| 项目类型 | 测试用例数 | 关键测试点 |
|---------|-----------|----------|
| SpringBoot | 40 | 依赖解析、任务执行 |
| Kotlin JVM | 30 | 编译、测试 |
| Java | 30 | 编译、测试、打包 |

---

## 八、风险和缓解

### 8.1 风险

1. **AGP版本差异大**：AGP 7.x-9.x API变化显著
2. **KTS DSL复杂性**：KTS解析比Groovy复杂
3. **测试环境**：需要多个AGP版本测试

### 8.2 缓解策略

1. 使用适配器模式隔离版本差异
2. 优先支持Groovy DSL，KTS作为扩展
3. 使用容器化测试环境

---

## 九、附录

### A. 相关资源

- AGP源码: `gradle/libs/com.android.tools.build/builder-model-9.3.0-alpha06-sources/`
- Gradle Tooling API: `gradle/libs/android/zero/studio/gradle/gradle-tooling-api/9.5.1/`

### B. 关键接口映射

| 现有接口 | AGP 9.x对应 | 说明 |
|---------|------------|------|
| `AndroidProject` | `AndroidProject` | v2接口 |
| `Variant` | `Variant` | v2接口 |
| `VariantDependencies` | `VariantDependencies` | v2接口 |
| N/A | `Versions` | 新增 |

### C. 错误代码

| 错误代码 | 含义 | 解决方案 |
|---------|------|---------|
| `INIT_FAILED` | 初始化失败 | 检查AGP版本 |
| `MODEL_INCOMPATIBLE` | 模型不兼容 | 更新适配器 |
| `SERIALIZATION_ERROR` | 序列化失败 | 实现Serializable |
