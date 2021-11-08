/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
        google()
    }

    val kotlinVersion: String by project
    dependencies {
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    kotlin("jvm") apply false
    id("org.jetbrains.dokka")
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

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
                    "customStyleSheets": ["${rootProject.file("docs/dokka-presets/css/logo-styles.css")}"],
                    "customAssets": [
                        "${rootProject.file("docs/dokka-presets/assets/logo-icon.svg")}",
                        "${rootProject.file("docs/dokka-presets/assets/aws_logo_white_59x35.png")}"
                    ],
                    "footerMessage": "© $year, Amazon Web Services, Inc. or its affiliates. All rights reserved.",
                    "separateInheritedMembers" : true
                }
            """
        )
        pluginsMapConfiguration.set(pluginConfigMap)
    }
}

val localProperties: Map<String, Any> by lazy {
    val props = Properties()

    listOf(
        File(rootProject.projectDir, "local.properties"), // Project-specific local properties
        File(rootProject.projectDir.parent, "local.properties"), // Workspace-specific local properties
        File(System.getProperty("user.home"), ".sdkdev/local.properties"), // User-specific local properties
    )
        .filter(File::exists)
        .map(File::inputStream)
        .forEach(props::load)

    props.mapKeys { (k, _) -> k.toString() }
}

fun Project.prop(name: String): Any? =
    this.properties[name] ?: localProperties[name]

if (project.prop("kotlinWarningsAsErrors")?.toString()?.toBoolean() == true) {
    subprojects {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.allWarningsAsErrors = true
        }
    }
}

// configure the root multimodule docs
tasks.dokkaHtmlMultiModule.configure {
    moduleName.set("Smithy SDK")

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

apply(from = rootProject.file("gradle/codecoverage.gradle"))

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
    "smithy-kotlin-codegen/src/**/*.kt",
    "runtime/**/*.kt",
    "benchmarks/**/jvm/*.kt",
)

tasks.register<JavaExec>("ktlint") {
    description = "Check Kotlin code style."
    group = "Verification"
    classpath = configurations.getByName("ktlint")
    main = "com.pinterest.ktlint.Main"
    args = lintPaths
}

tasks.register<JavaExec>("ktlintFormat") {
    description = "Auto fix Kotlin code style violations"
    group = "formatting"
    classpath = configurations.getByName("ktlint")
    main = "com.pinterest.ktlint.Main"
    args = listOf("-F") + lintPaths
}
