/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configurePublishing
import aws.sdk.kotlin.gradle.kmp.*
import aws.sdk.kotlin.gradle.util.typedProp
import org.gradle.kotlin.dsl.withType
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.aws.kotlin.repo.tools.kmp) apply false
    jacoco
}

val sdkVersion: String by project

// Apply KMP configuration from build plugin
configureKmpTargets()

// Disable cross compilation if flag is set
val disableCrossCompile = typedProp<Boolean>("aws.kotlin.native.disableCrossCompile") == true
if (disableCrossCompile) {
    logger.warn("aws.kotlin.native.disableCrossCompile=true: Cross compilation is disabled.")
    allprojects {
        disableCrossCompileTargets()
    }
}

// capture locally - scope issue with custom KMP plugin
val libraries = libs

subprojects {
    if (!needsKmpConfigured) return@subprojects
    group = "aws.smithy.kotlin"
    version = sdkVersion

    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("org.jetbrains.dokka")
        plugin(libraries.plugins.aws.kotlin.repo.tools.kmp.get().pluginId)
    }

    configurePublishing("smithy-kotlin", "smithy-lang")
    kotlin {
        explicitApi()

        sourceSets {
            // dependencies available for all subprojects
            named("commonMain") {
                dependencies {
                    // refactor to only add this to projects that need it
                    implementation(libraries.kotlinx.coroutines.core)
                }
            }

            named("commonTest") {
                dependencies {
                    implementation(libraries.kotest.assertions.core)
                }
            }

            named("jvmTest") {
                dependencies {
                    implementation(libraries.kotlinx.coroutines.debug)
                    implementation(libraries.kotest.assertions.core.jvm)
                }
            }
        }
    }

    kotlin.sourceSets.all {
        // Allow subprojects to use internal APIs
        // See https://kotlinlang.org/docs/reference/opt-in-requirements.html#opting-in-to-using-api
        listOf("kotlin.RequiresOptIn", "kotlinx.cinterop.ExperimentalForeignApi").forEach { languageSettings.optIn(it) }
    }

    dependencies {
        dokkaPlugin(project(":dokka-smithy"))
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")

            // FIXME When building LinuxX64 on AL2 the linker inclues a bunch of dynamic links to unavailable versions
            //  of zlib. The below workaround forces the linker to statically link zlib but it's a hack because the
            //  linker will still dynamically link zlib (although the executable will no longer fail at runtime due to
            //  link resolution failures). The correct solution for this is probably containerized builds similar to
            //  what we do in aws-crt-kotlin. The following compiler args were helpful in debugging this issue:
            //  * Enable verbose compiler output                        : -verbose
            //  * Increase verbosity during the compiler's linker phase : -Xverbose-phases=Linker
            //  * Enable verbose linker output from gold                : -linker-option --verbose
            if (target.contains("linux", ignoreCase = true)) {
                freeCompilerArgs.addAll(
                    listOf(
                        "-linker-option", // The subsequent argument is for the linker
                        "-Bstatic", // Enable static linking for the libraries that follow
                        "-linker-option", // The subsequent argument is for the linker
                        "-lz", // Link zlib statically (because of -Bstatic above)
                        "-linker-option", // The subsequent argument is for the linker
                        "-Bdynamic", // Restore dynamic linking, which is the default
                    ),
                )
            }
        }
    }

    smithyConfigureIosSimulatorTasks()
}


/**
 * Disables standalone mode in simulator tests since it causes issues with TLS.
 * This means we need to manage the simulator state ourselves (booting, shutting down).
 * https://youtrack.jetbrains.com/issue/KT-38317
 */
public fun Project.smithyConfigureIosSimulatorTasks() {
    val simulatorDeviceName = project.findProperty("iosSimulatorDevice") as? String ?: "iPhone 15"

    val xcrun = "/usr/bin/xcrun"

    tasks.register("bootIosSimulatorDevice", Exec::class.java) {
        isIgnoreExitValue = true
        commandLine(xcrun, "simctl", "boot", simulatorDeviceName)

        doLast {
            val result = executionResult.get()
            val code = result.exitValue
            if (code != 148 && code != 149) { // ignore "simulator already running" errors
                result.assertNormalExitValue()
            }
        }
    }

    tasks.register("shutdownIosSimulatorDevice", Exec::class.java) {
        isIgnoreExitValue = true
        mustRunAfter(tasks.withType<KotlinNativeSimulatorTest>())
        commandLine(xcrun, "simctl", "shutdown", simulatorDeviceName)

        doLast {
            val result = executionResult.get()
            val code = result.exitValue
            if (code != 148 && code != 149) { // ignore "simulator already shutdown" errors
                result.assertNormalExitValue()
            }
        }
    }

    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        if (!HostManager.hostIsMac) {
            return@configureEach
        }

        dependsOn("bootIosSimulatorDevice")
        finalizedBy("shutdownIosSimulatorDevice")

        standalone.set(false)
        device.set(simulatorDeviceName)
    }
}
