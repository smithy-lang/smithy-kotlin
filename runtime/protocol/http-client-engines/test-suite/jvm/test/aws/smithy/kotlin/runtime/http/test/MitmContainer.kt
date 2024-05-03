/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test

import aws.smithy.kotlin.runtime.http.test.util.Docker
import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.Volume
import java.io.Closeable

private const val CONTAINER_MOUNT_POINT = "/home/mitmproxy/scripts"
private const val CONTAINER_PORT = 8080
private const val IMAGE_NAME = "mitmproxy/mitmproxy:8.1.0"
private val PROXY_SCRIPT_ROOT = System.getProperty("MITM_PROXY_SCRIPTS_ROOT") // defined by gradle script

// Port used for communication with container
private val exposedPort = ExposedPort.tcp(CONTAINER_PORT)

/**
 * A Docker container which runs the **mitmproxy** image. Upon instantiating this class, a docker container will be
 * created and ran with a logger attached echoing logs out to **STDOUT**. The container will be stopped and removed when
 * [close] is called.
 */
class MitmContainer(vararg options: String) : Closeable {
    private val delegate: Docker.Container

    init {
        val cmd = listOf(
            "mitmdump", // https://docs.mitmproxy.org/stable/#mitmdump
            "--flow-detail",
            "2",
            "-s",
            "$CONTAINER_MOUNT_POINT/fakeupstream.py",
            *options,
        ).also { println("Initializing container with command: $it") }

        // Make proxy scripts from host filesystem available in container's filesystem
        val binding = Bind(PROXY_SCRIPT_ROOT, Volume(CONTAINER_MOUNT_POINT), AccessMode.ro)

        delegate = Docker.Instance.createContainer(IMAGE_NAME, cmd, binding, exposedPort)

        try {
            delegate.apply {
                start()
                waitUntilReady()
            }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    /**
     * Gets the host port that can be used to communicate to the MITM proxy
     */
    val hostPort: Int
        get() = delegate.hostPort

    override fun close() = delegate.close()
}
