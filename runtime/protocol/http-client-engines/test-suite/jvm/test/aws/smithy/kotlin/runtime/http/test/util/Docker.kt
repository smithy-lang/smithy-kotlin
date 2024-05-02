/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.util.OsFamily
import aws.smithy.kotlin.runtime.util.PlatformProvider
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.AsyncDockerCmd
import com.github.dockerjava.api.command.SyncDockerCmd
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

private val DOCKER_HOST = URI.create("unix:///var/run/docker.sock")
private val MAX_POLL_TIME = 10.seconds
private const val POLL_CONNECT_TIMEOUT_MS = 100
private val POLL_INTERVAL = 250.milliseconds

/**
 * Wrapper class for the Docker client
 */
class Docker {
    companion object {
        val Instance by lazy { Docker() }
    }

    private val client = run {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()

        val httpClient = ZerodepDockerHttpClient.Builder()
            .dockerHost(DOCKER_HOST)
            .build()

        DockerClientImpl.getInstance(config, httpClient)
    }

    fun createContainer(
        imageName: String,
        cmd: List<String>,
        bind: Bind,
        exposedPort: ExposedPort,
        loggingHandler: (String) -> Unit = ::println,
    ): Container {
        ensureImageExists(imageName, loggingHandler)

        val portBinding = PortBinding(Ports.Binding.empty(), exposedPort)

        val hostConfig = HostConfig
            .newHostConfig()
            .withBinds(bind)
            .withPortBindings(portBinding)

        val id = client
            .createContainerCmd(imageName)
            .withHostConfig(hostConfig)
            .withExposedPorts(exposedPort)
            .withCmd(cmd)
            .execAndMeasure { "Created container ${it.id}" }
            .id
            .substring(0..<12) // Short container IDs are 12 chars vs full container IDs at 64 chars

        val loggerAdapter = LoggerAdapter<Frame>(loggingHandler) { it.payload.decodeToString() }

        client
            .attachContainerCmd(id)
            .withFollowStream(true)
            .withStdOut(true)
            .withStdErr(true)
            .withLogs(true)
            .execAndMeasure(loggerAdapter) { "Attached logger to container $id" }
            .awaitStarted()

        return Container(id, exposedPort)
    }

    private fun ensureImageExists(imageName: String, loggingHandler: (String) -> Unit) {
        val exists = client
            .listImagesCmd()
            .withReferenceFilter(imageName)
            .execAndMeasure { "Checking for $imageName locally (exists = ${it.any()})" }
            .any()

        if (!exists) {
            val loggerAdapter = LoggerAdapter<PullResponseItem>(loggingHandler) { it.status }

            client
                .pullImageCmd(imageName)
                .execAndMeasure(loggerAdapter) { "Started image pull for $imageName" }
                .awaitCompletion()
        }
    }

    inner class Container(val id: String, val exposedPort: ExposedPort) : Closeable {
        private val poller = Poller(MAX_POLL_TIME, POLL_INTERVAL)

        override fun close() {
            client
                .removeContainerCmd(id)
                .withForce(true)
                .exec()
                .also { println("Container $id removed") }
        }

        val hostPort: Int by lazy {
            poller.pollNotNull("Port $exposedPort in container $id") {
                client
                    .inspectContainerCmd(id)
                    .exec()
                    .networkSettings
                    .ports
                    .bindings[exposedPort]
                    ?.first()
                    ?.hostPortSpec
                    ?.toInt()
            }
        }

        private fun isReady() =
            Socket().use { socket ->
                val endpoint = InetSocketAddress(InetAddress.getLocalHost(), hostPort)
                try {
                    socket.connect(endpoint, POLL_CONNECT_TIMEOUT_MS)
                    true
                } catch (e: IOException) {
                    false
                }
            }

        fun start() {
            client.startContainerCmd(id).execAndMeasure { "Container $id running" }
        }

        fun waitUntilReady() = poller.pollTrue("Socket localHost:$hostPort → $exposedPort on container $id", ::isReady)
    }
}

private class LoggerAdapter<I>(
    val handler: (String) -> Unit,
    val converter: (I) -> String?,
) : ResultCallback.Adapter<I>() {
    override fun onNext(value: I?) {
        value?.let(converter)?.let(handler)
    }
}

private fun <T> SyncDockerCmd<T>.execAndMeasure(msg: (T) -> String): T {
    val (value, duration) = measureTimedValue {
        exec()
    }
    println("${msg(value)} in $duration")
    return value
}

private fun <C : AsyncDockerCmd<C, T>?, T, I : ResultCallback<T>> AsyncDockerCmd<C, T>.execAndMeasure(
    input: I,
    msg: (I) -> String,
): I {
    val (value, duration) = measureTimedValue {
        exec(input)
    }
    println("${msg(value)} in $duration")
    return value
}
