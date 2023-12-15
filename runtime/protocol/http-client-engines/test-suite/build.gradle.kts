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

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":runtime:protocol:http-client"))
                implementation(project(":runtime:protocol:http-test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":runtime:testing"))
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.server.jetty)
                implementation(libs.ktor.network.tls.certificates)

                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-default"))
                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-crt"))

                implementation(libs.slf4j.simple)
            }
        }

        jvmAndNativeMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junit.jupiter)
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

    @Input
    lateinit var sslConfigPath: String

    @TaskAction
    fun exec() {
        try {
            println("[TestServers] start")
            val urlClassLoaderSource = classpath.map { file -> file.toURI().toURL() }.toTypedArray()
            val loader = URLClassLoader(urlClassLoaderSource, ClassLoader.getSystemClassLoader())

            val mainClass = loader.loadClass(main)
            val main = mainClass.getMethod("startServers", String::class.java)
            server = main.invoke(null, sslConfigPath) as Closeable
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
    sslConfigPath = File.createTempFile("ssl-", ".cfg").absolutePath
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
    systemProperty("SSL_CONFIG_PATH", startTestServers.sslConfigPath)
    val enableProxyTestsProp = "aws.test.http.enableProxyTests"
    val runningInCodeBuild = System.getenv().containsKey("CODEBUILD_BUILD_ID")
    systemProperty(enableProxyTestsProp, System.getProperties().getOrDefault(enableProxyTestsProp, !runningInCodeBuild))
}

gradle.buildFinished {
    startTestServers.stop()
}
