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
                implementation(libs.ktor.server.jetty.jakarta)
                implementation(libs.ktor.network.tls.certificates)
                implementation(libs.okhttp)

                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-default"))
                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-crt"))
                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-okhttp"))
                implementation(project(":runtime:protocol:http-client-engines:http-client-engine-okhttp4"))

                implementation(libs.slf4j.simple)
            }
        }

        jvmAndNativeMain {
            dependencies {
                implementation(libs.ktor.server.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.docker.core)
                implementation(libs.docker.transport.zerodep)

                implementation(project(":runtime:observability:telemetry-defaults"))
            }
        }

        all {
            languageSettings.optIn("aws.smithy.kotlin.runtime.InternalApi")
        }
    }
}

abstract class TestServerProvider :
    BuildService<TestServerProvider.Params>,
    AutoCloseable {
    interface Params : BuildServiceParameters {
        val sslConfigPath: Property<String>
        val classpath: ConfigurableFileCollection
    }

    private var server: Closeable? = null

    fun startServers() {
        if (server != null) return

        try {
            println("[TestServers] start")
            val urlClassLoaderSource = parameters.classpath.map { it.toURI().toURL() }.toTypedArray()
            val loader = URLClassLoader(urlClassLoaderSource, ClassLoader.getSystemClassLoader())

            val mainClass = loader.loadClass("aws.smithy.kotlin.runtime.http.test.util.TestServersKt")
            val main = mainClass.getMethod("startServers", String::class.java)
            server = main.invoke(null, parameters.sslConfigPath.get()) as Closeable
            println("[TestServers] started")
        } catch (cause: Throwable) {
            println("[TestServers] failed: ${cause.message}")
            throw cause
        }
    }

    override fun close() {
        server?.close()
        server = null
        println("[TestServers] stopped")
    }
}

val testServerProvider = gradle.sharedServices.registerIfAbsent("testServers", TestServerProvider::class) {
    parameters.sslConfigPath.set(File.createTempFile("ssl-", ".cfg").absolutePath)
}

afterEvaluate {
    testServerProvider.get().parameters.classpath.from(kotlin.targets.getByName("jvm").compilations["test"].runtimeDependencyFiles!!)
}

abstract class StartTestServersTask : DefaultTask() {
    @get:Internal
    abstract val serverProvider: Property<TestServerProvider>

    @TaskAction
    fun start() {
        serverProvider.get().startServers()
    }
}

val osName = System.getProperty("os.name")

val startTestServers = tasks.register<StartTestServersTask>("startTestServers") {
    dependsOn(tasks["jvmJar"])
    usesService(testServerProvider)
    serverProvider.set(testServerProvider)
}

val testTasks = listOf("allTests", "jvmTest")
    .forEach {
        tasks.named(it) {
            dependsOn(startTestServers)
            usesService(testServerProvider)
        }
    }

tasks.jvmTest {
    // set test environment for proxy tests
    systemProperty("MITM_PROXY_SCRIPTS_ROOT", projectDir.resolve("proxy-scripts").absolutePath)
    systemProperty("SSL_CONFIG_PATH", testServerProvider.get().parameters.sslConfigPath.get())

    val enableProxyTestsProp = "aws.test.http.enableProxyTests"
    val runningInCodeBuild = System.getenv().containsKey("CODEBUILD_BUILD_ID")
    val runningInLinux = System.getProperty("os.name").contains("Linux", ignoreCase = true)
    val shouldRunProxyTests = !runningInCodeBuild && runningInLinux

    systemProperty(enableProxyTestsProp, System.getProperties().getOrDefault(enableProxyTestsProp, shouldRunProxyTests))
}
