package com.itsaky.androidide.templates.impl.androidstudio.activity.archStarterActivity

import com.itsaky.androidide.templates.base.AndroidModuleTemplateBuilder
import com.itsaky.androidide.templates.base.composeDependencies
import com.itsaky.androidide.templates.impl.base.createRecipe

internal fun AndroidModuleTemplateBuilder.archStarterActivityRecipe() = createRecipe {
  composeDependencies()
  sources {
    writeKtSrc(data.packageName, "MyApplication", source = ::myApplicationKt)
    writeKtSrc("${data.packageName}.ui", "MainActivity", source = ::mainActivityKt)
    writeKtSrc("${data.packageName}.ui", "Navigation", source = ::navigationKt)
    writeKtSrc("${data.packageName}.ui.mymodel", "MyModelScreen", source = ::myModelScreenKt)
    writeKtSrc("${data.packageName}.ui.mymodel", "MyModelViewModel", source = ::myModelViewModelKt)
    writeKtSrc("${data.packageName}.data", "MyModelRepository", source = ::myModelRepositoryKt)
    writeKtSrc("${data.packageName}.data.di", "DataModule", source = ::dataModuleKt)
    writeKtSrc("${data.packageName}.data.local.database", "AppDatabase", source = ::appDatabaseKt)
    writeKtSrc("${data.packageName}.data.local.database", "MyModel", source = ::myModelKt)
    writeKtSrc("${data.packageName}.data.local.di", "DatabaseModule", source = ::databaseModuleKt)
    writeKtSrc("${data.packageName}.ui.theme", "Color", source = ::colorKt)
    writeKtSrc("${data.packageName}.ui.theme", "Theme", source = ::themeKt)
    writeKtSrc("${data.packageName}.ui.theme", "Type", source = ::typeKt)
  }
}


private fun AndroidModuleTemplateBuilder.myApplicationKt() = "package ${data.packageName}\nclass MyApplication"
private fun AndroidModuleTemplateBuilder.mainActivityKt() = "package ${data.packageName}.ui\nclass MainActivity"
private fun AndroidModuleTemplateBuilder.navigationKt() = "package ${data.packageName}.ui\nobject Navigation"
private fun AndroidModuleTemplateBuilder.myModelScreenKt() = "package ${data.packageName}.ui.mymodel\nobject MyModelScreen"
private fun AndroidModuleTemplateBuilder.myModelViewModelKt() = "package ${data.packageName}.ui.mymodel\nclass MyModelViewModel"
private fun AndroidModuleTemplateBuilder.myModelRepositoryKt() = "package ${data.packageName}.data\nclass MyModelRepository"
private fun AndroidModuleTemplateBuilder.dataModuleKt() = "package ${data.packageName}.data.di\nobject DataModule"
private fun AndroidModuleTemplateBuilder.appDatabaseKt() = "package ${data.packageName}.data.local.database\nabstract class AppDatabase"
private fun AndroidModuleTemplateBuilder.myModelKt() = "package ${data.packageName}.data.local.database\ndata class MyModel(val id:Int=0)"
private fun AndroidModuleTemplateBuilder.databaseModuleKt() = "package ${data.packageName}.data.local.di\nobject DatabaseModule"
private fun AndroidModuleTemplateBuilder.colorKt() = "package ${data.packageName}.ui.theme\nobject Color"
private fun AndroidModuleTemplateBuilder.themeKt() = "package ${data.packageName}.ui.theme\nobject Theme"
private fun AndroidModuleTemplateBuilder.typeKt() = "package ${data.packageName}.ui.theme\nobject Type"
