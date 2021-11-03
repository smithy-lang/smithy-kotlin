/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
plugins {
    kotlin("jvm")
    jacoco
    `maven-publish`
}

description = "Generates Kotlin code from Smithy models"
extra["displayName"] = "Smithy :: Kotlin :: Codegen"
extra["moduleName"] = "software.amazon.smithy.kotlin.codegen"

val sdkVersion: String by project
group = "software.amazon.smithy.kotlin"
version = sdkVersion

val smithyVersion: String by project
val kotlinVersion: String by project
val junitVersion: String by project
val kotestVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api("software.amazon.smithy:smithy-codegen-core:$smithyVersion")
    api("software.amazon.smithy:smithy-waiters:$smithyVersion")
    implementation("software.amazon.smithy:smithy-aws-traits:$smithyVersion")
    implementation("software.amazon.smithy:smithy-protocol-test-traits:$smithyVersion")

    // Test dependencies
    // These are not set as test dependencies so they can be shared with other modules
    implementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    implementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    implementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
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
        versionFile.writeText("$version")
    }
}

// unlike the runtime, smithy-kotlin codegen package is not expected to run on Android...we can target 1.8
tasks.compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
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

apply(from = rootProject.file("gradle/publish.gradle"))
