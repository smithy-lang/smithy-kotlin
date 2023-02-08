/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import java.io.*
import java.net.*

description = "Common HTTP Client Engine Test Suite"
extra["moduleName"] = "aws.smithy.kotlin.http.test"

extra["skipPublish"] = true

val coroutinesVersion: String by project
val ktorVersion: String by project
val slf4jVersion: String by project
val testContainersVersion: String by project

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:protocol:http-test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation(project(":runtime:testing"))
            }
        }

        jvmMain {
            dependencies {
                implementation("io.ktor:ktor-server-cio:$ktorVersion")

                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-default"))
                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-crt"))

                implementation("org.slf4j:slf4j-simple:$slf4jVersion")
            }
        }

        jvmTest {
            dependencies {
                implementation("org.testcontainers:testcontainers:$testContainersVersion")
                implementation("org.testcontainers:junit-jupiter:$testContainersVersion")
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
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

tasks.jvmTest {
    // set test environment for proxy tests
    systemProperty("MITM_PROXY_SCRIPTS_ROOT", projectDir.resolve("proxy-scripts").absolutePath)
    val enableProxyTestsProp = "aws.test.http.enableProxyTests"
    systemProperty(enableProxyTestsProp, System.getProperties().getOrDefault(enableProxyTestsProp, "true"))
}

gradle.buildFinished {
    startTestServer.stop()
}