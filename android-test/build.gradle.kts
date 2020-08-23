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
description = "Instrumented Android integration tests"

plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

apply(from = rootProject.file("gradle/android.gradle"))

android {
    defaultConfig {
        println("min sdk: ${this.minSdkVersion}")
        multiDexKeepProguard = File("proguard-multidex-rules.pro")
    }

    sourceSets {
        // by default instrumented androidTest doesn't look for kotlin files
        val androidTest by getting {
            java.srcDirs("src/androidTest/kotlin")
        }
    }

    packagingOptions {
        exclude("META-INF/AL2.0")
        exclude("META-INF/LGPL2.1")
        exclude("META-INF/ktor-io.kotlin_module")
    }
}


kotlin {
    sourceSets {
        this.names.forEach { println(it) }
        val androidTest by getting {
            dependencies {
                implementation("androidx.multidex:multidex:2.0.1")
                implementation("com.android.support:support-annotations:28.0.0")
                implementation("com.android.support.test:runner:1.0.2")

                implementation(project(":client-runtime:serde"))
                implementation(project(":client-runtime:serde:serde-json"))
                implementation(project(":client-runtime:client-rt-core"))

                // TODO - HTTP
            }
        }
    }
}

