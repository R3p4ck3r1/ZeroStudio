package com.itsaky.androidide.templates.impl.androidstudio.activity.archStarterActivity

import com.itsaky.androidide.templates.escapeKotlinIdentifier

class ArchStarterActivityTemplateVariables(
    val basePackage: String,
    val appName: String,
    val activityName: String,
    val modelName: String,
    val themeName: String,
) {
  fun packageName(vararg subpackages: String) =
      escapeKotlinIdentifier(listOf(basePackage, *subpackages).joinToString("."))

  fun packageDeclaration(vararg subpackages: String) = "package ${packageName(*subpackages)}"

  val dataPackage get() = packageName("data")
  val dataDiPackage get() = packageName("data", "di")
  val databasePackage get() = packageName("data", "local", "database")
  val dataLocalDiPackage get() = packageName("data", "local", "di")
  val repositoryName get() = "${modelName}Repository"
  val repositoryVarName get() = "${modelName.lowercaseFirst()}Repository"
  val repositoryNameQualified get() = "$dataPackage.$repositoryName"
  val themePackage get() = packageName("ui", "theme")
  val modelPackage get() = packageName("ui", modelName.lowercase())
  val viewModelName get() = "${modelName}ViewModel"
  val modelDao get() = "${modelName}Dao"
  val modelDaoVar get() = "${modelName.lowercaseFirst()}Dao"
  val dataModelQualified get() = "$databasePackage.$modelName"
  val modelDaoQualified get() = "$databasePackage.$modelDao"
  val modelScreen get() = "${modelName}Screen"
  val modelScreenQualified get() = "$modelPackage.$modelScreen"
  val modelUiState get() = "${modelName}UiState"
  val modelUiStateQualified get() = "$modelPackage.${modelName}UiState"
  val themeNameQualified get() = "${packageName("ui", "theme")}.$themeName"
}

private fun String.lowercaseFirst() = if (isEmpty()) "" else first().lowercase() + substring(1)
