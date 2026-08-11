import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

object AppVersionParser {
    data class AppVersion(
        val name: String,
        val code: Int,
    )

    private val semVerPattern = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")

    fun encodeVersionCode(major: Long, minor: Long, patch: Long): Int {
        require(minor < 1_000) { "minor must be less than 1000" }
        require(patch < 1_000) { "patch must be less than 1000" }
        val code = major * 1_000_000 + minor * 1_000 + patch
        require(code in 1..Int.MAX_VALUE) { "versionCode must fit a positive Android Int" }
        return code.toInt()
    }

    fun parse(value: String): AppVersion {
        val match = semVerPattern.matchEntire(value)
            ?: error("versionName must use SemVer X.Y.Z without leading zeroes: $value")
        val major = match.groupValues[1].toLongOrNull() ?: error("major is too large: $value")
        val minor = match.groupValues[2].toLongOrNull() ?: error("minor is too large: $value")
        val patch = match.groupValues[3].toLongOrNull() ?: error("patch is too large: $value")
        return AppVersion(value, encodeVersionCode(major, minor, patch))
    }
}

abstract class CheckVersionTask : DefaultTask() {
    @TaskAction
    fun verify() {
        check(AppVersionParser.parse("1.0.0").code == 1_000_000)
        check(AppVersionParser.parse("2.7.13").code == 2_007_013)
        check(AppVersionParser.parse("10.0.0").code == 10_000_000)
        check(runCatching { AppVersionParser.parse("1.1000.0") }.isFailure)
        check(runCatching { AppVersionParser.parse("01.2.3") }.isFailure)
        check(runCatching { AppVersionParser.parse("1.2") }.isFailure)
    }
}

val versionFile = rootProject.file("version.txt")
val appVersion = AppVersionParser.parse(versionFile.readText().trim())
rootProject.extra["appVersionName"] = appVersion.name
rootProject.extra["appVersionCode"] = appVersion.code

tasks.register<CheckVersionTask>("checkVersion")
