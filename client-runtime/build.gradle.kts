/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    jacoco
}

val platforms = listOf("common", "jvm")

fun projectNeedsPlatform(project: Project, platform: String ): Boolean {
    val files = project.projectDir.listFiles()
    val hasPosix = files.any { it.name == "posix" }
    val hasDarwin = files.any { it.name == "darwin" }

    if (hasPosix && platform == "darwin") return false
    if (hasDarwin && platform == "posix") return false
    if (!hasPosix && !hasDarwin && platform == "darwin") return false
    return files.any{ it.name == "common" || it.name == platform }
}

subprojects {
    group = "com.amazonaws"
    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("org.jetbrains.dokka")
        plugin("jacoco")
    }

    println("Configuring: $project")

    // this works by iterating over each platform name and inspecting the projects files. If the project contains
    // a directory with the corresponding platform name we apply the common configuration settings for that platform
    // (which includes adding the multiplatform target(s)). This makes adding platform support easy and implicit in each
    // subproject.
    platforms.forEach { platform ->
        if (projectNeedsPlatform(project, platform)) {
            configure(listOf(project)){
                apply(from = rootProject.file("gradle/${platform}.gradle"))
            }
        }
    }

    kotlin {
        sourceSets {
            all {
                languageSettings.progressiveMode = true
                val srcDir = if (name.endsWith("Main")) "src" else "test"
                val resourcesPrefix = if (name.endsWith("Test")) "test-" else  ""
                // the name is always the platform followed by a suffix of either "Main" or "Test" (e.g. jvmMain, commonTest, etc)
                val platform = name.substring(0, name.length - 4)
                kotlin.srcDir("$platform/$srcDir")
                resources.srcDir("$platform/${resourcesPrefix}resources")
            }
        }
    }

    tasks.dokka {
        outputFormat = "html"
        outputDirectory = "$buildDir/kdoc"
    }

//    tasks.test {
//        useJUnitPlatform()
//        testLogging {
//            events("passed", "skipped", "failed")
//            showStandardStreams = true
//        }
//    }

//    // Configure jacoco (code coverage) to generate an HTML report
//    tasks.jacocoTestReport {
//        reports {
//            xml.isEnabled = false
//            csv.isEnabled = false
//            html.destination = file("$buildDir/reports/jacoco")
//        }
//    }

    // Always run the jacoco test report after testing.
//    tasks["test"].finalizedBy(tasks["jacocoTestReport"])
//    tasks {
//        named<JacocoReport>("jacocoTestReport") {
//            reports {
//                xml.isEnabled = false
//                csv.isEnabled = false
//                html.destination = file("$buildDir/reports/jacoco")
//            }
//        }
//    }
}

