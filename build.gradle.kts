/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configureLinting
import aws.sdk.kotlin.gradle.dsl.configureNexus
import aws.sdk.kotlin.gradle.util.typedProp
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import kotlin.apply

buildscript {
    // NOTE: buildscript classpath for the root project is the parent classloader for the subprojects, we
    // only need to add e.g. atomic-fu and build-plugins here for imports and plugins to be available in subprojects.
    // NOTE: Anything included in the root buildscript classpath is added to the classpath for all projects!
    dependencies {
        classpath(libs.kotlinx.atomicfu.plugin)
        // Add our custom gradle build logic to buildscript classpath
        classpath(libs.aws.kotlin.repo.tools.build.support)
    }
}

plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    // ensure the correct version of KGP ends up on our buildscript classpath
    // since build-plugins also has <some> version in its dependency closure
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.aws.kotlin.repo.tools.artifactsizemetrics)
}

artifactSizeMetrics {
    artifactPrefixes = setOf(":runtime")
    significantChangeThresholdPercentage = 5.0
    projectRepositoryName = "smithy-kotlin"
}

val testJavaVersion = typedProp<String>("test.java.version")?.let {
    JavaLanguageVersion.of(it)
}?.also {
    println("configuring tests to run with jdk $it")
}

allprojects {
    tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaTask>().configureEach {
        val sdkVersion: String by project
        moduleVersion.set(sdkVersion)

        val year = java.time.LocalDate.now().year
        val pluginConfigMap = mapOf(
            "org.jetbrains.dokka.base.DokkaBase" to """
                {
                    "customStyleSheets": [
                        "${rootProject.file("docs/dokka-presets/css/logo-styles.css")}",
                        "${rootProject.file("docs/dokka-presets/css/aws-styles.css")}"
                    ],
                    "customAssets": [
                        "${rootProject.file("docs/dokka-presets/assets/logo-icon.svg")}",
                        "${rootProject.file("docs/dokka-presets/assets/aws_logo_white_59x35.png")}",
                        "${rootProject.file("docs/dokka-presets/scripts/accessibility.js")}"
                    ],
                    "footerMessage": "© $year, Amazon Web Services, Inc. or its affiliates. All rights reserved.",
                    "separateInheritedMembers" : true,
                    "templatesDir": "${rootProject.file("docs/dokka-presets/templates")}"
                }
            """,
        )
        pluginsMapConfiguration.set(pluginConfigMap)
    }

    if (rootProject.typedProp<Boolean>("kotlinWarningsAsErrors") == true) {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.allWarningsAsErrors = true
        }
    }

    if (testJavaVersion != null) {
        tasks.withType<Test> {
            // JDK8 tests fail with out of memory sometimes, not sure why...
            maxHeapSize = "2g"
            val toolchains = project.extensions.getByType<JavaToolchainService>()
            javaLauncher.set(
                toolchains.launcherFor {
                    languageVersion.set(testJavaVersion)
                },
            )
        }
    }

    // Enables running `./gradlew allDeps` to get a comprehensive list of dependencies for every subproject
    tasks.register<DependencyReportTask>("allDeps") { }
}

// configure the root multimodule docs
tasks.dokkaHtmlMultiModule.configure {
    moduleName.set("Smithy Kotlin")

    // Output subprojects' docs to <docs-base>/project-name/* instead of <docs-base>/path/to/project-name/*
    // This is especially important for inter-repo linking (e.g., via externalDocumentationLink) because the
    // package-list doesn't contain enough project path information to indicate where modules' documentation are
    // located.
    fileLayout.set { parent, child ->
        parent.outputDirectory.dir(child.moduleName)
    }

    includes.from(
        // NOTE: these get concatenated
        rootProject.file("docs/dokka-presets/README.md"),
    )

    val excludeFromDocumentation = listOf(
        project(":runtime:testing"),
        project(":runtime:smithy-test"),
    )
    removeChildTasks(excludeFromDocumentation)
}

// Publishing
configureNexus()

// Code Style
val lintPaths = listOf(
    "**/*.{kt,kts}",
    "!**/generated-src/**",
    "!**/smithyprojections/**",
)

configureLinting(lintPaths)

// Binary compatibility
apiValidation {
    ignoredProjects.addAll(
        setOf(
            "dokka-smithy",
            "aws-signing-tests",
            "test-suite",
            "http-test",
            "smithy-test",
            "testing",
            "smithy-kotlin-codegen",
            "smithy-kotlin-codegen-testutils",
            "smithy-aws-kotlin-codegen",
            "protocol-tests",
            "aws-signing-benchmarks",
            "channel-benchmarks",
            "http-benchmarks",
            "serde-benchmarks",
            "serde-codegen-support",
            "serde-tests",
            "nullability-tests",
            "paginator-tests",
            "waiter-tests",
            "compile",
            "slf4j-1x-consumer",
            "slf4j-2x-consumer",
        ),
    )
}

val disableCrossCompile = typedProp<Boolean>("aws.kotlin.native.disableCrossCompile") == true

if (disableCrossCompile) {
    logger.warn("aws.kotlin.native.disableCrossCompile=true: Cross compilation is disabled.")
    disableCrossCompileTargets()
}

private val KotlinNativeTarget.isLinux: Boolean
    get() = konanTarget.family == Family.LINUX

private val KotlinNativeTarget.isApple: Boolean
    get() = konanTarget.family.isAppleFamily

private val KotlinNativeTarget.isWindows: Boolean
    get() = konanTarget.family == Family.MINGW

/**
 * Kotlin/Native Linux and Windows targets are generally enabled on all hosts since
 * the Kotlin toolchain and backend compilers support cross compilation. We
 * are using cinterop and have to compile CRT for those platforms which sometimes
 * requires using docker which isn't always available in CI or setup in users environment.
 *
 * See [KT-30498](https://youtrack.jetbrains.com/issue/KT-30498)
 */
fun Project.disableCrossCompileTargets() {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinNativeTarget> {
                val knTarget = this
                when {
                    HostManager.hostIsMac && (knTarget.isLinux || knTarget.isWindows) -> disable(knTarget)
                    HostManager.hostIsLinux && knTarget.isApple -> disable(knTarget)
                    HostManager.hostIsMingw && (knTarget.isLinux || knTarget.isApple) -> disable(knTarget)
                }
            }
        }
    }
}

internal fun Project.disable(knTarget: KotlinNativeTarget) {
    logger.warn("disabling Kotlin/Native target: ${knTarget.name}")
    knTarget.apply {
        compilations.all {
            cinterops.all {
                tasks.named(interopProcessingTaskName).configure { enabled = false }
            }
            compileTaskProvider.configure { enabled = false }
        }

        binaries.all {
            linkTaskProvider.configure { enabled = false }
        }

        mavenPublication {
            tasks.withType<AbstractPublishToMaven>().configureEach {
                onlyIf { publication != this@mavenPublication }
            }
            tasks.withType<GenerateModuleMetadata>().configureEach {
                onlyIf { publication != this@mavenPublication }
            }
        }
    }
}
