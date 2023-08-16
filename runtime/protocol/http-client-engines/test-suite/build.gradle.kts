/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import aws.sdk.kotlin.gradle.dsl.skipPublishing
import java.io.Closeable
import java.net.URLClassLoader

description = "Common HTTP Client Engine Test Suite"
extra["moduleName"] = "aws.smithy.kotlin.http.test"

skipPublishing()

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

                implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
            }
        }

        jvmMain {
            dependencies {
                implementation("io.ktor:ktor-server-jetty:$ktorVersion")

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

open class LocalTestServers : DefaultTask() {
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
            println("[TestServers] start")
            val urlClassLoaderSource = classpath.map { file -> file.toURI().toURL() }.toTypedArray()
            val loader = URLClassLoader(urlClassLoaderSource, ClassLoader.getSystemClassLoader())

            val mainClass = loader.loadClass(main)
            val main = mainClass.getMethod("startServers")
            server = main.invoke(null) as Closeable
            println("[TestServers] started")
        } catch (cause: Throwable) {
            println("[TestServers] failed: ${cause.message}")
            throw cause
        }
    }

    fun stop() {
        if (server != null) {
            server?.close()
            println("[TestServers] stop")
        }
    }
}

val osName = System.getProperty("os.name")

val startTestServers = task<LocalTestServers>("startTestServers") {
    dependsOn(tasks["jvmJar"])

    main = "aws.smithy.kotlin.runtime.http.test.util.TestServersKt"
    val kotlinCompilation = kotlin.targets.getByName("jvm").compilations["test"]
    classpath = (kotlinCompilation as org.jetbrains.kotlin.gradle.plugin.KotlinCompilationToRunnableFiles<*>).runtimeDependencyFiles
}

val testTasks = listOf("allTests", "jvmTest")
    .forEach {
        tasks.named(it) {
            dependsOn(startTestServers)
        }
    }

tasks.jvmTest {
    // set test environment for proxy tests
    systemProperty("MITM_PROXY_SCRIPTS_ROOT", projectDir.resolve("proxy-scripts").absolutePath)
    val enableProxyTestsProp = "aws.test.http.enableProxyTests"
    systemProperty(enableProxyTestsProp, System.getProperties().getOrDefault(enableProxyTestsProp, "true"))
}

gradle.buildFinished {
    startTestServers.stop()
}
