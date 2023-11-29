/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configurePublishing
plugins {
    kotlin("jvm")
    jacoco
    `maven-publish`
}

description = "Generates Kotlin code from Smithy models"
extra["displayName"] = "Smithy :: Kotlin :: Codegen"
extra["moduleName"] = "software.amazon.smithy.kotlin.codegen"

val codegenVersion: String by project
group = "software.amazon.smithy.kotlin"
version = codegenVersion

val sdkVersion: String by project
val runtimeVersion = sdkVersion

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api(libs.smithy.codegen.core)
    api(libs.smithy.waiters)
    implementation(libs.smithy.rules.engine)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.jsoup)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core.jvm)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(project(":codegen:smithy-kotlin-codegen-testutils"))
}

val generateSdkRuntimeVersion by tasks.registering {
    // generate the version of the runtime to use as a resource.
    // this keeps us from having to manually change version numbers in multiple places
    val resourcesDir = "$buildDir/resources/main/software/amazon/smithy/kotlin/codegen/core"
    val versionFile = file("$resourcesDir/sdk-version.txt")
    val gradlePropertiesFile = rootProject.file("gradle.properties")
    inputs.file(gradlePropertiesFile)
    outputs.file(versionFile)
    sourceSets.main.get().output.dir(resourcesDir)
    doLast {
        versionFile.writeText("$runtimeVersion")
    }
}

// unlike the runtime, smithy-kotlin codegen package is not expected to run on Android...we can target 1.8
tasks.compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    dependsOn(generateSdkRuntimeVersion)
}

tasks.compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
}

// Reusable license copySpec
val licenseSpec = copySpec {
    from("${project.rootDir}/LICENSE")
    from("${project.rootDir}/NOTICE")
}

// Configure jars to include license related info
tasks.jar {
    metaInf.with(licenseSpec)
    inputs.property("moduleName", project.name)
    manifest {
        attributes["Automatic-Module-Name"] = project.name
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    group = "publishing"
    description = "Assembles Kotlin sources jar"
    classifier = "sources"
    from(sourceSets.getByName("main").allSource)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Configure jacoco (code coverage) to generate an HTML report
tasks.jacocoTestReport {
    reports {
        xml.isEnabled = false
        csv.isEnabled = false
        html.destination = file("$buildDir/reports/jacoco")
    }
}

// Always run the jacoco test report after testing.
tasks["test"].finalizedBy(tasks["jacocoTestReport"])

publishing {
    publications {
        create<MavenPublication>("codegen") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}

configurePublishing("smithy-kotlin")
