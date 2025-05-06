/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.LocalDate

/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.jvm)
}

description = "Custom Dokka plugin for Kotlin Smithy SDK API docs"

dependencies {
    compileOnly(libs.dokka.base)
    compileOnly(libs.dokka.core)

    testImplementation(libs.jsoup)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core.jvm)
    testImplementation(libs.kotlin.test.junit5)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.dokkaHtml)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        allWarningsAsErrors.set(false) // FIXME Dokka bundles stdlib into the classpath, causing an unfixable warning
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaTask>().configureEach {
    val sdkVersion: String by project
    moduleVersion.set(sdkVersion)

    val year = LocalDate.now().year
    val pluginConfigMap = mapOf(
        "org.jetbrains.dokka.base.DokkaBase" to """
                {
                    "customStyleSheets": [
                        "${rootProject.file("docs/dokka-presets/css/logo-styles.css").absolutePath.replace("\\", "/")}",
                        "${rootProject.file("docs/dokka-presets/css/aws-styles.css").absolutePath.replace("\\", "/")}"
                    ],
                    "customAssets": [
                        "${rootProject.file("docs/dokka-presets/assets/logo-icon.svg").absolutePath.replace("\\", "/")}",
                        "${rootProject.file("docs/dokka-presets/assets/aws_logo_white_59x35.png").absolutePath.replace("\\", "/")}",
                        "${rootProject.file("docs/dokka-presets/scripts/accessibility.js").absolutePath.replace("\\", "/")}"
                    ],
                    "footerMessage": "© $year, Amazon Web Services, Inc. or its affiliates. All rights reserved.",
                    "separateInheritedMembers" : true,
                    "templatesDir": "${rootProject.file("docs/dokka-presets/templates").absolutePath.replace("\\", "/")}"
                }
            """,
    )
    pluginsMapConfiguration.set(pluginConfigMap)
}
