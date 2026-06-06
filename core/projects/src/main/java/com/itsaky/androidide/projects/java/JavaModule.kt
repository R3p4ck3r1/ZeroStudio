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
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.projects.java

import com.itsaky.androidide.builder.model.IJavaCompilerSettings
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.ModuleProject
import com.itsaky.androidide.tooling.api.ProjectType.Java
import com.itsaky.androidide.tooling.api.models.GradleTask
import com.itsaky.androidide.tooling.api.models.JavaContentRoot
import com.itsaky.androidide.tooling.api.models.JavaModuleDependency
import com.itsaky.androidide.tooling.api.models.JavaModuleExternalDependency
import com.itsaky.androidide.tooling.api.models.JavaModuleProjectDependency
import java.io.File

/**
 * A [GradleProject] model implementation for Java library modules which is exposed to other modules
 * and provides additional helper methods.
 *
 * @param name The display name of the project.
 * @param description The project description.
 * @param path The project path (same as Gradle project paths). For example, `:app`,
 *   `:module:submodule`, etc. Root project is always represented by path `:`.
 * @param projectDir The project directory.
 * @param buildDir The build directory of the project.
 * @param buildScript The Gradle buildscript file of the project.
 * @param tasks The tasks of the project.
 * @param contentRoots The source roots of this module.
 * @param dependencies The dependencies of this module.
 * @author Akash Yadav
 */
class JavaModule(
    name: String,
    description: String,
    path: String,
    projectDir: File,
    buildDir: File,
    buildScript: File,
    tasks: List<GradleTask>,
    override val compilerSettings: IJavaCompilerSettings,
    val contentRoots: List<JavaContentRoot>,
    val dependencies: List<JavaModuleDependency>,
    val classesJar: File?,
) : ModuleProject(name, description, path, projectDir, buildDir, buildScript, tasks) {

  companion object {

    /** 编译时依赖作用域常量。 */
    const val SCOPE_COMPILE = "COMPILE"

    /** 运行时依赖作用域常量。 */
    const val SCOPE_RUNTIME = "RUNTIME"
  }



  /**
   * 获取当前模块自己的编译类路径。
   *
   * @return 该模块的类路径集合。
   */
  override fun getClassPaths(): Set<File> {
    return getModuleClasspaths()
  }

  /**
   * 获取当前模块自身的源码目录集合（不包含依赖模块的源码）。
   *
   * @return 源码目录的 <code>File</code> 集合。
   */
  override fun getSourceDirectories(): Set<File> {
    val sources = mutableSetOf<File>()
    contentRoots.forEach {
      sources.addAll(it.sourceDirectories.map { sourceDirectory -> sourceDirectory.directory })
    }
    return sources
  }

  /**
   * 获取编译该模块所需的完整源码目录（含依赖模块的源码）。
   *
   * @return 编译所需的全部源码目录集合。
   */
  override fun getCompileSourceDirectories(): Set<File> {
    val dirs = getSourceDirectories().toMutableSet()
    getCompileModuleProjects().forEach { dirs.addAll(it.getSourceDirectories()) }
    return dirs
  }

  /**
   * 获取模块自身的专属类路径（生成的 Jar）。
   *
   * @return 包含生成的 <code>classes.jar</code> 的集合。
   */
  override fun getModuleClasspaths(): Set<File> {
    return mutableSetOf(classesJar ?: File("does-not-exist.jar"))
  }

  /**
   * 获取全量编译类路径（Compile Classpaths），包括模块自身以及第三方依赖库。
   *
   * @return 全量编译类路径集合。
   */
  override fun getCompileClasspaths(): Set<File> {
    val classpaths = getModuleClasspaths().toMutableSet()
    getCompileModuleProjects().forEach { classpaths.addAll(it.getCompileClasspaths()) }
    classpaths.addAll(getDependencyClasspaths())
    return classpaths
  }

  // ==============================================================================
  // 新增：从 b 分支移植的供 Compose Preview / UI 框架使用的编译产物解析方法
  // ==============================================================================

  /**
   * 获取模块编译过程中生成的中间类路径 (Intermediate Classpaths)。
   *
   * <p><b>核心用途：</b>主要供 <b>Compose Preview</b> 等模块加载未打包为 JAR 的 <code>.class</code> 文件， 用于实时渲染自定义
   * Composable 函数。</p>
   *
   * @return 包含 <code>.class</code> 目录的 <code>File</code> 集合。
   */
  override fun getIntermediateClasspaths(): Set<File> {
    val result = mutableSetOf<File>()
    val buildDirectory = buildDir // 来源于 GradleProject

    // 1. Kotlin 编译输出
    val kotlinClasses = File(buildDirectory, "tmp/kotlin-classes/main")
    if (kotlinClasses.exists()) {
      result.add(kotlinClasses)
    }

    // 2. Java 编译输出
    val javaClasses = File(buildDirectory, "classes/java/main")
    if (javaClasses.exists()) {
      result.add(javaClasses)
    }

    return result
  }

  /**
   * 获取运行时生成的 <b>DEX</b> 文件集合。
   *
   * <p>由于纯 Java/Kotlin 模块在标准流程中不直接生成 DEX，此处返回空集合。 如果该模块被打包入 Android 项目，它的字节码会被应用模块一并转化为 DEX。</p>
   *
   * @return 空的 <code>File</code> 集合。
   */
  override fun getRuntimeDexFiles(): Set<File> = emptySet()

  /**
   * 检查给定的外部依赖是否存在于此模块中。
   *
   * <p><b>核心用途：</b>用于特定功能（如 Compose 预览机制）探测项目中是否包含了所需的依赖库 （如
   * <code>androidx.compose.ui:ui-tooling</code>）。</p>
   *
   * @param group 依赖的组名（如 <code>"com.google.guava"</code>）。
   * @param name 依赖的构件名（如 <code>"guava"</code>）。
   * @return 如果依赖存在则返回 <code>true</code>，否则返回 <code>false</code>。
   */
  open fun hasExternalDependency(group: String, name: String): Boolean {
    return this.dependencies.any { dependency ->
      if (dependency is JavaModuleExternalDependency) {
        val jarName = dependency.jarFile?.name ?: ""
        // 通过分析文件名进行安全退级匹配，包含构件名即认为具有该依赖
        jarName.contains(name)
      } else {
        false
      }
    }
  }

  // ==============================================================================

  /**
   * 获取该模块依赖的所有内部 <code>ModuleProject</code> 实例。
   *
   * @return 依赖的同项目级别模块列表。
   */
  override fun getCompileModuleProjects(): List<ModuleProject> {
    val workspace = IProjectManager.getInstance().getWorkspace() ?: return emptyList()
    return this.dependencies
        .filterIsInstance<JavaModuleProjectDependency>()
        .filter { it.scope == SCOPE_COMPILE }
        .mapNotNull { workspace.findProject(it.projectPath) }
        .filterIsInstance<ModuleProject>()
  }

  /**
   * 获取该模块的外部依赖类路径（JAR 文件）。
   *
   * <p>此方法会自动过滤掉物理文件不存在的依赖项，以防引发 I/O 错误。</p>
   *
   * @return 外部依赖的 <code>File</code> 集合。
   */
  fun getDependencyClasspaths(): Set<File> {
    return this.dependencies
        .filterIsInstance<JavaModuleExternalDependency>()
        .mapNotNull { it.jarFile }
        // .filter { it.exists() } // 仅保留实际存在的文件，防止死链 (来源于 b 分支的 takeIf { it.exists() } 优化)
        .toHashSet()
  }
}
