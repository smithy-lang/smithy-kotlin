/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
buildscript {
    repositories {
        google()
        jcenter()
    }

    val kotlinVersion: String by project
    dependencies {
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    kotlin("jvm") version "1.4.31" apply false
    id("org.jetbrains.dokka") version "1.4.20"
    maven
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
        google()
    }

    /*
    dependencyLocking {
        lockAllConfigurations()
    }

    tasks.register("resolveAndLockAll") {
        doFirst {
            require(gradle.startParameter.isWriteDependencyLocks)
        }
        doLast {
            configurations.filter {
                // Add any custom filtering on the configurations to be resolved
                it.isCanBeResolved
            }.forEach { it.resolve() }
        }
    }
    */
}

apply(from = rootProject.file("gradle/codecoverage.gradle"))

val ktlint by configurations.creating
val ktlintVersion: String by project

dependencies {
    ktlint("com.pinterest:ktlint:$ktlintVersion")
    ktlint(project(":ktlint-rules"))
}

val lintPaths = listOf(
    "smithy-kotlin-codegen/src/**/*.kt",
    "client-runtime/**/*.kt"
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

/*
task("createPom") {
    group = "build"
    description = "Generate a Maven pom.xml in the root based on Gradle config (used for GitHub dependency scans)"

    val sdkVersion: String by project

    outputs.file("pom.xml")

    inputs.property("group", project.group)
    inputs.property("name", project.name)
    inputs.property("pomVersion", sdkVersion)
    inputs.file("gradle.properties")

    doLast {
        allprojects
            .filter { it.plugins.hasPlugin("maven-publish") }
            .forEach { project ->
                project.the<MavenPluginConvention>().pom() {
                    project {
                        groupId = project.group.toString()
                        artifactId = project.name
                        version = sdkVersion
                    }
                }.writeTo("pom.xml")
            }
    }
}

tasks.getByPath("build").dependsOn("createPom")
*/
