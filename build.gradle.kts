/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configureLinting
import aws.sdk.kotlin.gradle.dsl.configureMinorVersionStrategyRules
import aws.sdk.kotlin.gradle.publishing.SonatypeCentralPortalPublishTask
import aws.sdk.kotlin.gradle.publishing.SonatypeCentralPortalWaitForPublicationTask
import aws.sdk.kotlin.gradle.util.typedProp
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    // NOTE: buildscript classpath for the root project is the parent classloader for the subprojects, we
    // only need to add e.g. atomic-fu and build-plugins here for imports and plugins to be available in subprojects.
    // NOTE: Anything included in the root buildscript classpath is added to the classpath for all projects!
    dependencies {
        classpath(libs.kotlinx.atomicfu.plugin)
        // Add our custom gradle build logic to buildscript classpath
        classpath(libs.aws.kotlin.repo.tools.build.support)
        /*
        Enforce jackson to a version supported both by dokka and jreleaser:
        https://github.com/Kotlin/dokka/issues/3472#issuecomment-1929712374
        https://github.com/Kotlin/dokka/issues/3194#issuecomment-1929382630
         */
        classpath(enforcedPlatform("com.fasterxml.jackson:jackson-bom:2.15.3"))
    }

    configurations.classpath {
        resolutionStrategy {
            /*
            Version bumping the SDK to 1.5.x in repo tools broke our buildscript classpath:
            java.lang.NoSuchMethodError: 'void kotlinx.coroutines.CancellableContinuation.resume(java.lang.Object, kotlin.jvm.functions.Function3)

            FIXME: Figure out what broke our buildscript classpath, this is a temporary fix
             */
            force("com.squareup.okhttp3:okhttp-coroutines:5.0.0-alpha.14")
        }
    }
}

plugins {
    `dokka-convention`
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    alias(libs.plugins.aws.kotlin.repo.tools.artifactsizemetrics)
    // ensure the correct version of KGP ends up on our buildscript classpath
    // since build-plugins also has <some> version in its dependency closure
    id(libs.plugins.kotlin.multiplatform.get().pluginId) apply false
    id(libs.plugins.kotlin.jvm.get().pluginId) apply false
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
    tasks.withType<KotlinCompile> {
        compilerOptions {
            allWarningsAsErrors = true
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
dokka {
    moduleName.set("Smithy Kotlin")

    dokkaPublications.html {
        includes.from(
            rootProject.file("docs/dokka-presets/README.md"),
        )
    }
}

dependencies {
    dokka(project(":runtime"))
}

// Publishing
val sdkVersion: String by project
tasks.register<SonatypeCentralPortalPublishTask>("publishToCentralPortal") {
    bundle.set(rootProject.layout.buildDirectory.file("aws-smithy-kotlin-$sdkVersion.tar.gz"))
}
tasks.register<SonatypeCentralPortalWaitForPublicationTask>("waitForCentralPortalPublication") { }

// Code Style
val lintPaths = listOf(
    "**/*.{kt,kts}",
    "!**/generated-src/**",
    "!**/smithyprojections/**",
    "!**/build/**",
)

configureLinting(lintPaths)
configureMinorVersionStrategyRules(lintPaths)

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
