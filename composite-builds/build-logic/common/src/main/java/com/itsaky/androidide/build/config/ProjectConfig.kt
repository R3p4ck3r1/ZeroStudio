package com.itsaky.androidide.build.config

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.gradle.api.Project

/** @author Akash Yadav */
object ProjectConfig {

  const val REPO_HOST =
      /** "github.com" */
      BuildConfig.REPO_HOST
  const val REPO_OWNER =
      /** "AndroidIDEOfficial" */
      BuildConfig.REPO_OWNER
  const val REPO_NAME =
      /** "AndroidIDE" */
      BuildConfig.REPO_NAME
  const val REPO_URL =
      /** "https://$REPO_HOST/$REPO_OWNER/$REPO_NAME" */
      BuildConfig.REPO_URL
  const val SCM_GIT = BuildConfig.SCM_GIT
  /** "scm:git:git://$REPO_HOST/$REPO_OWNER/$REPO_NAME.git" */
  const val SCM_SSH = BuildConfig.SCM_SSH
  /** "scm:git:ssh://git@$REPO_HOST/$REPO_OWNER/$REPO_NAME.git" */
  const val PROJECT_SITE =
      /** "https://m.androidide.com" */
      BuildConfig.PROJECT_SITE
}

private var shouldPrintNotAGitRepoWarning = true
private var shouldPrintVersionName = true
private var shouldPrintVersionCode = true
private var shouldPrintDailyCounter = true
private var shouldPrintGitShortSha = true

/** Gradle property: 当天打包递增计数器(1~99), 用于拼接版本号/版本代码. */
const val PROP_DAILY_BUILD_COUNTER = "ide.build.dailyCounter"

/** Gradle property: 当前 git 提交哈希的 7 位简写. */
const val PROP_GIT_SHORT_SHA = "ide.build.gitShortSha"

/** Helper function to get the current date in YYYYMMDD format. */
internal fun getCurrentDateVersion(): String {
  val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
  return LocalDate.now().format(dateFormatter)
}

/**
 * 当天打包递增计数器, 2 位 0 填充 (e.g. "01", "02", ..., "99").
 *
 * 优先读取 gradle 属性 `-Pide.build.dailyCounter`(由 CI 工作流在每次构建前自动计算);
 * 未提供 / 非法时回退为 "01", 方便本地开发调试.
 */
val Project.dailyBuildCounter: String
  get() {
    val raw = (findProperty(PROP_DAILY_BUILD_COUNTER) as? String)?.trim().orEmpty()
    val parsed = raw.toIntOrNull()?.coerceIn(1, 99) ?: 1
    val formatted = "%02d".format(parsed)
    if (shouldPrintDailyCounter) {
      val source = if (raw.isEmpty()) "default" else "gradle property"
      logger.warn("Daily build counter is '$formatted' (from $source).")
      shouldPrintDailyCounter = false
    }
    return formatted
  }

/**
 * 当前打包时 git 提交哈希的 7 位简写.
 *
 * 优先读取 gradle 属性 `-Pide.build.gitShortSha`(CI 工作流通过 `${{ github.sha }}` 注入);
 * 未提供时尝试 `git rev-parse --short=7 HEAD`(本地开发);
 * 失败时回退为 "local", 避免空字符串.
 */
val Project.gitShortSha: String
  get() {
    val fromProperty = (findProperty(PROP_GIT_SHORT_SHA) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    if (fromProperty != null) {
      if (shouldPrintGitShortSha) {
        logger.warn("Git short SHA is '$fromProperty' (from gradle property).")
        shouldPrintGitShortSha = false
      }
      return fromProperty
    }

    val fromGit =
        runCatching {
              val proc =
                  ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
                      .directory(rootDir)
                      .redirectErrorStream(true)
                      .start()
              proc.waitFor()
              if (proc.exitValue() != 0) return@runCatching null
              proc.inputStream.bufferedReader().readText().trim()
            }
            .getOrNull()
            ?.takeIf { it.matches(Regex("^[0-9a-f]{7,40}$")) }
    val resolved = fromGit ?: "local"
    if (shouldPrintGitShortSha) {
      val source = if (fromGit != null) "git rev-parse" else "fallback"
      logger.warn("Git short SHA is '$resolved' (from $source).")
      shouldPrintGitShortSha = false
    }
    return resolved
  }

/** Whether this build is being executed in the F-Droid build server. */
val Project.isFDroidBuild: Boolean
  get() {
    if (!FDroidConfig.hasRead) {
      FDroidConfig.load(this)
    }
    return com.itsaky.androidide.build.config.FDroidConfig.isFDroidBuild
  }

// val Project.simpleVersionName: String
// get() {

// if (!CI.isGitRepo) {
// if (shouldPrintNotAGitRepoWarning) {
// logger.warn("Unable to infer version name. The build is not running on a git repository.")
// shouldPrintNotAGitRepoWarning = false
// }

// return "1.0.0-beta"
// }

// val version = rootProject.version.toString()
// val regex = Regex("^v\\d+\\.?\\d+\\.?\\d+-\\w+")

// val simpleVersion = regex.find(version)?.value?.substring(1)?.also {
// if (shouldPrintVersionName) {
// logger.warn("Simple version name is '$it' (from version $version)")
// shouldPrintVersionName = false
// }
// }

// if (simpleVersion == null) {
// if (CI.isTestEnv) {
// return "1.0.0-beta"
// }

// throw IllegalStateException(
// "Cannot extract simple version name. Invalid version string '$version'. Version names must be
// SEMVER with 'v' prefix"
// )
// }

// return simpleVersion
// }

// private var shouldPrintVersionCode = true
// val Project.projectVersionCode: Int
// get() {

// val version = simpleVersionName
// val regex = Regex("^\\d+\\.?\\d+\\.?\\d+")

// val versionCode = regex.find(version)?.value?.replace(".", "")?.toInt()?.also {
// if (shouldPrintVersionCode) {
// logger.warn("Version code is '$it' (from version ${version}).")
// shouldPrintVersionCode = false
// }
// }
// ?: throw IllegalStateException(
// "Cannot extract version code. Invalid version string '$version'. Version names must be SEMVER
// with 'v' prefix"
// )

// return versionCode
// }

// val Project.publishingVersion: String
// get() {

// var publishing = simpleVersionName
// if (isFDroidBuild) {
// // when building for F-Droid, the release is already published so we should have
// // the maven dependencies already published
// // simply return the simple version name here.
// return publishing
// }

// if (CI.isCiBuild && CI.isGitRepo && CI.branchName != "main") {
// publishing += "-${CI.commitHash}-SNAPSHOT"
// }

// return publishing
// }

/**
 * Generates a version name in the format `vYYYYMMDD-NN-sha7`, where:
 * - YYYYMMDD is today's date (Asia/Shanghai or system local),
 * - NN is the daily build counter (1~99, 2-digit zero padded),
 * - sha7 is the first 7 characters of the current git commit hash.
 *
 * Example: `v20260613-02-8a3f2b`.
 */
val Project.simpleVersionName: String
  get() {
    val dateVersion = getCurrentDateVersion()
    val counter = dailyBuildCounter
    val sha = gitShortSha
    val version = "v${dateVersion}-${counter}-${sha}"

    if (shouldPrintVersionName) {
      logger.warn("Simple version name is '$version' (date + daily counter + git short SHA).")
      shouldPrintVersionName = false
    }
    return version
  }

/**
 * Generates the project version code as `YYYYMMDDC` (an integer, no zero padding on the tail),
 * where:
 * - YYYYMMDD is today's date (8 digits),
 * - C is the daily build counter (1~9: 9 digits total; 10~99: 10 digits total).
 *
 * Examples:
 * - date `20260613`, counter `1` -> `202606131`
 * - date `20260613`, counter `2` -> `202606132`
 * - date `20260613`, counter `42` -> `2026061342`
 *
 * The value stays below Android's `versionCode` upper bound of 2,100,000,000 for any date
 * within the 21st century (max `2099123199`).
 */
val Project.projectVersionCode: Int
  get() {
    val dateString = getCurrentDateVersion()
    val dateInt =
        dateString.toIntOrNull()
            ?: throw IllegalStateException(
                "Cannot extract version code. Invalid date string '$dateString'.")
    val counterInt = dailyBuildCounter.toIntOrNull() ?: 1
    // 注意: 这里用 * 10 而不是 * 100, 这样末尾的计数器不会被强制零填充.
    // 例: counter=1 -> 20260613 * 10 + 1 = 202606131 (而不是 2026061301).
    val versionCode = dateInt * 10 + counterInt

    if (shouldPrintVersionCode) {
      logger.warn("Version code is '$versionCode' (date + unpadded daily counter).")
      shouldPrintVersionCode = false
    }
    return versionCode
  }

val Project.publishingVersion: String
  get() {
    var publishing = simpleVersionName
    if (isFDroidBuild) {
      // when building for F-Droid, the release is already published so we should have
      // the maven dependencies already published
      // simply return the simple version name here.
      return publishing
    }

    if (
        CI.isCiBuild &&
            CI.branchName !=
                /** "ZeroStudio" */
                BuildConfig.GIT_Branch_Name
    ) {
      publishing += "-${CI.commitHash}-SNAPSHOT"
    }

    return publishing
  }

/**
 * The version name which is used to download the artifacts at runtime.
 *
 * The value varies based on the following cases :
 * - For CI and F-Droid builds: same as [publishingVersion].
 * - For local builds: `latest.integration` to make sure that Gradle downloads the latest snapshots.
 */
val Project.downloadVersion: String
  get() {
    return if (CI.isCiBuild || isFDroidBuild) {
      publishingVersion
    } else {
      // sometimes, when working locally, Gradle fails to download the latest snapshot version
      // this may cause issues while initializing the project in AndroidIDE
      VersionUtils.getLatestSnapshotVersion("gradle-plugin")
    }
  }
