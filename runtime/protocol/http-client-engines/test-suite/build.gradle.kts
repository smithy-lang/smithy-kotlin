/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
import java.io.*
import java.net.*

description = "Common HTTP Client Engine Test Suite"
extra["moduleName"] = "aws.smithy.kotlin.http.test"

extra["skipPublish"] = true

val coroutinesVersion: String by project
val ktorVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":runtime:protocol:http"))
                implementation(project(":runtime:protocol:http-test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }

        jvmMain {
            dependencies {
                implementation("io.ktor:ktor-server-cio:$ktorVersion")

                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-ktor"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.util.InternalApi")
        }
    }
}

open class LocalTestServer : DefaultTask() {
    @Internal
    var server: Closeable? = null
        private set

    @Internal
    lateinit var main: String

    @Internal
    lateinit var classpath: FileCollection

    @TaskAction
    fun exec() {
        try {
            println("[TestServer] start")
            val urlClassLoaderSource = classpath.map { file -> file.toURI().toURL() }.toTypedArray()
            val loader = URLClassLoader(urlClassLoaderSource, ClassLoader.getSystemClassLoader())

            val mainClass = loader.loadClass(main)
            val main = mainClass.getMethod("startServer")
            server = main.invoke(null) as Closeable
            println("[TestServer] started")
        } catch (cause: Throwable) {
            println("[TestServer] failed: ${cause.message}")
            throw cause
        }
    }

    fun stop() {
        if (server != null) {
            server?.close()
            println("[TestServer] stop")
        }
    }
}

val osName = System.getProperty("os.name")

val startTestServer = task<LocalTestServer>("startTestServer") {
    dependsOn(tasks["jvmJar"])

    main = "aws.smithy.kotlin.runtime.http.test.util.TestServerKt"
    val kotlinCompilation = kotlin.targets.getByName("jvm").compilations["test"]
    classpath = (kotlinCompilation as org.jetbrains.kotlin.gradle.plugin.KotlinCompilationToRunnableFiles<*>).runtimeDependencyFiles
}

val testTasks = listOf("allTests", "jvmTest")
    .forEach {
        tasks.named(it) {
            dependsOn(startTestServer)
        }
    }

gradle.buildFinished {
    startTestServer.stop()
}