/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
// https://github.com/gradle/gradle/issues/15383
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.jetbrains.dokka")
}

val libs = rootProject.the<LibrariesForLibs>()

dokka {
    val sdkVersion: String by project
    moduleVersion.set(sdkVersion)

    // Increase heap memory allocated to Dokka's Gradle workers
    dokkaGeneratorIsolation = ProcessIsolation {
        maxHeapSize = "4g"
    }

    pluginsConfiguration.html {
        customStyleSheets.from(
            rootProject.file("docs/dokka-presets/css/aws-styles.css"),
        )

        customAssets.from(
            rootProject.file("docs/dokka-presets/assets/logo-icon.svg"),
            rootProject.file("docs/dokka-presets/scripts/accessibility.js"),
        )

        templatesDir.set(rootProject.file("docs/dokka-presets/templates"))

        footerMessage.set("© ${java.time.LocalDate.now().year}, Amazon Web Services, Inc. or its affiliates. All rights reserved.")
        separateInheritedMembers.set(true)
    }
}

val jacksonVersion = libs.jackson.bom.get().version!!

configurations.matching { it.name.startsWith("dokka") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.fasterxml.jackson")) {
            if (requested.name == "jackson-annotations") {
                // jackson-annotations dropped patch version from 2.20 onwards
                // https://github.com/FasterXML/jackson-annotations/issues/294
                useVersion(jacksonVersion.substringBeforeLast('.'))
            } else {
                useVersion(jacksonVersion)
            }
        }
    }
}

dependencies {
    dokkaPlugin(project(":dokka-smithy"))
}
