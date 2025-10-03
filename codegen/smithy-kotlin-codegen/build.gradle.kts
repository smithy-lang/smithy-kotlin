/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.configurePublishing
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    jacoco
    `maven-publish`
}

description = "Generates Kotlin code from Smithy models"
extra["displayName"] = "Smithy :: Kotlin :: Codegen"
extra["moduleName"] = "software.amazon.smithy.kotlin.codegen"

val codegenVersion: String by project
group = "software.amazon.smithy.kotlin"
version = codegenVersion

val runtimeVersion: Provider<String> = providers.gradleProperty("sdkVersion")

dependencies {
    api(libs.smithy.codegen.core)
    api(libs.smithy.waiters)
    implementation(libs.smithy.rules.engine)
    implementation(libs.smithy.aws.traits)
    implementation(libs.smithy.protocol.traits)
    implementation(libs.smithy.protocol.test.traits)
    implementation(libs.smithy.smoke.test.traits)
    implementation(libs.jsoup)

    // Test dependencies
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core.jvm)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(project(":codegen:smithy-kotlin-codegen-testutils"))
}

val generateSdkRuntimeVersion by tasks.registering {
    val resourcesDir = layout.buildDirectory.dir("resources/main/software/amazon/smithy/kotlin/codegen/core")
    val versionFile = resourcesDir.map { it.file("sdk-version.txt") }

    inputs.property("runtimeVersion", runtimeVersion)
    outputs.file(versionFile)

    doLast {
        val version = inputs.properties["runtimeVersion"] as String
        val file = versionFile.get().asFile
        file.writeText(version)
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=1.8")
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
    dependsOn(generateSdkRuntimeVersion)
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
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
    archiveClassifier.set("sources")
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
        xml.required.set(false)
        csv.required.set(false)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco"))
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

configurePublishing("smithy-kotlin", "smithy-lang")
