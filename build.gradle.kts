/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.kmp.typedProp

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    val kotlinVersion: String by project
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")

        // Add our custom gradle plugin(s) to buildscript classpath (comes from github source)
        // NOTE: buildscript classpath for the root project is the parent classloader for the subprojects, we
        // only need to include it here, imports in subprojects will work automagically
        classpath("aws.sdk.kotlin:build-plugins") {
            version {
                require("0.1.0")
            }
        }
    }
}

plugins {
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.12.1"
}

// configures (KMP) subprojects with our own KMP conventions and some default dependencies
apply(plugin="aws.sdk.kotlin.kmp")

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }

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
                        "${rootProject.file("docs/dokka-presets/assets/aws_logo_white_59x35.png")}"
                    ],
                    "footerMessage": "© $year, Amazon Web Services, Inc. or its affiliates. All rights reserved.",
                    "separateInheritedMembers" : true,
                    "templatesDir": "${rootProject.file("docs/dokka-presets/templates")}"
                }
            """,
        )
        pluginsMapConfiguration.set(pluginConfigMap)
    }
}

if (project.typedProp<Boolean>("kotlinWarningsAsErrors") == true) {
    subprojects {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.allWarningsAsErrors = true
        }
    }
}

// configure the root multimodule docs
tasks.dokkaHtmlMultiModule.configure {
    moduleName.set("Smithy Kotlin")

    // Output subprojects' docs to <docs-base>/project-name/* instead of <docs-base>/path/to/project-name/*
    // This is especially important for inter-repo linking (e.g., via externalDocumentationLink) because the
    // package-list doesn't contain enough project path information to indicate where modules' documentation are
    // located.
    fileLayout.set { parent, child -> parent.outputDirectory.get().resolve(child.project.name) }

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

if (
    project.hasProperty("sonatypeUsername") &&
    project.hasProperty("sonatypePassword") &&
    project.hasProperty("publishGroupName")
) {
    apply(plugin = "io.github.gradle-nexus.publish-plugin")

    val publishGroupName = project.property("publishGroupName") as String
    group = publishGroupName

    nexusPublishing {
        packageGroup.set(publishGroupName)
        repositories {
            create("awsNexus") {
                nexusUrl.set(uri("https://aws.oss.sonatype.org/service/local/"))
                snapshotRepositoryUrl.set(uri("https://aws.oss.sonatype.org/content/repositories/snapshots/"))
                username.set(project.property("sonatypeUsername") as String)
                password.set(project.property("sonatypePassword") as String)
            }
        }
    }
}

val ktlint by configurations.creating {
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}
val ktlintVersion: String by project

dependencies {
    ktlint("com.pinterest:ktlint:$ktlintVersion")
    ktlint(project(":ktlint-rules"))
}

val lintPaths = listOf(
    "**/*.{kt,kts}",
    "!**/generated-src/**",
    "!**/smithyprojections/**",
)

tasks.register<JavaExec>("ktlint") {
    description = "Check Kotlin code style."
    group = "Verification"
    classpath = configurations.getByName("ktlint")
    main = "com.pinterest.ktlint.Main"
    args = lintPaths
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.register<JavaExec>("ktlintFormat") {
    description = "Auto fix Kotlin code style violations"
    group = "formatting"
    classpath = configurations.getByName("ktlint")
    main = "com.pinterest.ktlint.Main"
    args = listOf("-F") + lintPaths
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

apiValidation {
    nonPublicMarkers.add("aws.smithy.kotlin.runtime.InternalApi")

    ignoredProjects.addAll(
        setOf(
            "dokka-smithy",
            "ktlint-rules",
            "aws-signing-tests",
            "test-suite",
            "http-test",
            "smithy-test",
            "testing",
            "smithy-kotlin-codegen",
            "smithy-kotlin-codegen-testutils",
            "aws-signing-benchmarks",
            "channel-benchmarks",
            "http-benchmarks",
            "serde-benchmarks",
            "serde-benchmarks-codegen",
            "paginator-tests",
            "waiter-tests",
            "compile",
        ),
    )
}
