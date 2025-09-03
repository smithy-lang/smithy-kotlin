/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configurePublishing
import aws.sdk.kotlin.gradle.kmp.*
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    `dokka-convention`
    alias(libs.plugins.aws.kotlin.repo.tools.kmp) apply false
    jacoco
}

val sdkVersion: String by project

// Apply KMP configuration from build plugin
configureKmpTargets()

// capture locally - scope issue with custom KMP plugin
val libraries = libs

subprojects {
    if (!needsKmpConfigured) return@subprojects
    group = "aws.smithy.kotlin"
    version = sdkVersion

    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
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

            findByName("jvmTest")?.run {
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
        languageSettings.optIn("kotlin.RequiresOptIn")
        if (!name.contains("jvm", ignoreCase = true) && !name.contains("common", ignoreCase = true)) {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xjdk-release=1.8")
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

    /*
        FIXME iOS simulator tests fail on GitHub CI with "Bad or unknown session":
        > Task :runtime:runtime-core:linkDebugTestIosSimulatorArm64
        java.lang.IllegalStateException: You have standalone simulator tests run mode disabled and tests have failed to run.

        The problem can be that you have not booted the required device or have configured the task to a different simulator. Please check the task output and its device configuration.
        > Task :runtime:runtime-core:iosSimulatorArm64Test
        If you are sure that your setup is correct, please file an issue: https://kotl.in/issue
        An error was encountered processing the command (domain=com.apple.CoreSimulator.SimError, code=405):
        Process spawn via launchd failed because device is not booted.
        Underlying error (domain=com.apple.SimLaunchHostService.RequestError, code=3):
            Bad or unknown session: com.apple.CoreSimulator.SimDevice.C120BDE1-C108-4759-842F-7D82B4E71E8C
     */
    tasks.withType<KotlinNativeSimulatorTest> {
        enabled = false
    }
}

// configureIosSimulatorTasks()

val excludeFromDocumentation = listOf(
    ":runtime:testing",
    ":runtime:smithy-test",
)

dependencies {
    subprojects.filterNot { excludeFromDocumentation.contains(it.path) }.forEach {
        it.plugins.apply("dokka-convention") // Apply the Dokka conventions plugin to the submodule
        dokka(project(it.path)) // Aggregate the submodule's generated documentation
    }

    subprojects {
        if (excludeFromDocumentation.contains(this@subprojects.path)) {
            return@subprojects
        }

        dokka {
            modulePath = this@subprojects.name
        }
    }
}
