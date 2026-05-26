package com.itsaky.androidide.repository.materials

import com.itsaky.androidide.projects.IProjectManager
import java.io.File

class ProjectMaterialsRepository {
  fun loadMaterials(): List<ProjectMaterialItem> {
    val projectDir = IProjectManager.getInstance().workspace?.projectDir
    return buildList {
      addAll(defaultApiMaterials())
      if (projectDir != null) addAll(collectProjectFiles(projectDir))
    }
  }

  private fun defaultApiMaterials() =
      listOf(
          ProjectMaterialItem("gradle-project", "GradleProject", MaterialSourceType.GRADLE_TOOLING_API, "org.gradle.tooling.model.GradleProject", "Gradle tasks/modules model."),
          ProjectMaterialItem("eclipse-project", "EclipseProject", MaterialSourceType.GRADLE_TOOLING_API, "org.gradle.tooling.model.eclipse.EclipseProject", "Eclipse source/classpath model."),
          ProjectMaterialItem("idea-project", "IdeaProject", MaterialSourceType.GRADLE_TOOLING_API, "org.gradle.tooling.model.idea.IdeaProject", "IDEA module/dependency model."),
          ProjectMaterialItem("android-project", "AndroidProject", MaterialSourceType.AGP_BUILDER_MODEL, "com.android.builder.model.AndroidProject", "Android variants/artifacts model."),
          ProjectMaterialItem("variant", "Variant", MaterialSourceType.AGP_BUILDER_MODEL, "com.android.builder.model.Variant", "Build variant outputs model."),
          ProjectMaterialItem("sdk-handler", "SdkHandler", MaterialSourceType.SDK_TOOLING, "com.android.sdklib.repository.AndroidSdkHandler", "Android SDK repository/materials provider."),
      )

  private fun collectProjectFiles(projectDir: File): List<ProjectMaterialItem> {
    val includeNames = setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "AndroidManifest.xml")
    return projectDir.walkTopDown().filter { it.isFile }.filter {
      it.name in includeNames || it.path.contains("/src/main/") || it.path.contains("/build/outputs/")
    }.take(300).map {
      ProjectMaterialItem("file:${it.absolutePath}", it.name, MaterialSourceType.PROJECT_FILE, "java.io.File", "Collected project material file.", it.absolutePath)
    }.toList()
  }
}
