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
                implementation(libs.docker.core)
                // FIXME docker-java has a ton of dependencies with vulnerabilities, and they don't seem motivated to fix them.
                // So we must override their dependencies with the latest patched versions. https://github.com/docker-java/docker-java/issues/1974
                implementation("com.fasterxml.jackson.core:jackson-databind:2.12.7.1") // https://github.com/docker-java/docker-java/issues/2177
                implementation("org.apache.commons:commons-compress:1.26.0") // https://github.com/docker-java/docker-java/pull/2256
                implementation("org.bouncycastle:bcpkix-jdk18on:1.78") // https://github.com/docker-java/docker-java/pull/2326

                implementation(libs.docker.transport.zerodep)
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
    val runningInLinux = System.getProperty("os.name").contains("Linux", ignoreCase = true)
    val shouldRunProxyTests = !runningInCodeBuild && runningInLinux

    systemProperty(enableProxyTestsProp, System.getProperties().getOrDefault(enableProxyTestsProp, shouldRunProxyTests))
}

gradle.buildFinished {
    startTestServers.stop()
}
