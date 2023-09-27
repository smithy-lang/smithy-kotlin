/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.*
import aws.sdk.kotlin.crt.io.SocketOptions
import aws.sdk.kotlin.crt.io.TlsContext
import aws.sdk.kotlin.crt.io.TlsContextOptionsBuilder
import aws.sdk.kotlin.crt.io.Uri
import aws.smithy.kotlin.runtime.config.TlsVersion
import aws.smithy.kotlin.runtime.crt.SdkDefaultIO
import aws.smithy.kotlin.runtime.http.HttpErrorCode
import aws.smithy.kotlin.runtime.http.HttpException
import aws.smithy.kotlin.runtime.http.engine.ProxyConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.io.Closeable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import aws.sdk.kotlin.crt.io.TlsContext as CrtTlsContext
import aws.sdk.kotlin.crt.io.TlsVersion as CrtTlsVersion
import aws.smithy.kotlin.runtime.config.TlsVersion as SdkTlsVersion

internal class ConnectionManager(
    private val config: CrtHttpEngineConfig,
) : Closeable {
    private val leases = Semaphore(config.maxConnections.toInt())

    private val crtTlsContext: TlsContext = TlsContextOptionsBuilder()
        .apply {
            verifyPeer = true
            alpn = config.tlsContext.alpn.joinToString(separator = ";") { it.protocolId }
            minTlsVersion = toCrtTlsVersion(config.tlsContext.minVersion)
        }
        .build()
        .let(::CrtTlsContext)

    private val options = HttpClientConnectionManagerOptionsBuilder().apply {
        clientBootstrap = config.clientBootstrap ?: SdkDefaultIO.ClientBootstrap
        tlsContext = crtTlsContext
        manualWindowManagement = true
        socketOptions = SocketOptions(
            connectTimeoutMs = config.connectTimeout.inWholeMilliseconds.toInt(),
        )
        initialWindowSize = config.initialWindowSizeBytes
        maxConnections = config.maxConnections.toInt()
        maxConnectionIdleMs = config.connectionIdleTimeout.inWholeMilliseconds
    }

    // connection managers are per host
    private val connManagers = mutableMapOf<String, HttpClientConnectionManager>()
    private val mutex = Mutex()

    public suspend fun acquire(request: HttpRequest): HttpClientConnection {
        val proxyConfig = config.proxySelector.select(request.url)

        val manager = getManagerForUri(request.uri, proxyConfig)
        var leaseAcquired = false

        return try {
            // wait for an actual connection
            val conn = withTimeout(config.connectionAcquireTimeout) {
                // get a permit to acquire a connection (limits overall connections since managers are per/host)
                leases.acquire()
                leaseAcquired = true
                manager.acquireConnection()
            }

            LeasedConnection(conn)
        } catch (ex: Exception) {
            if (leaseAcquired) {
                leases.release()
            }
            val httpEx = when (ex) {
                is HttpException -> ex
                is TimeoutCancellationException -> HttpException("timed out waiting for an HTTP connection to be acquired from the pool", errorCode = HttpErrorCode.CONNECTION_ACQUIRE_TIMEOUT)
                else -> HttpException(ex)
            }

            throw httpEx
        }
    }
    private suspend fun getManagerForUri(uri: Uri, proxyConfig: ProxyConfig): HttpClientConnectionManager = mutex.withLock {
        connManagers.getOrPut(uri.authority) {
            val connOpts = options.apply {
                this.uri = uri
                proxyOptions = when (proxyConfig) {
                    is ProxyConfig.Http -> HttpProxyOptions(
                        proxyConfig.url.host.toString(),
                        proxyConfig.url.port,
                        authUsername = proxyConfig.url.userInfo?.username,
                        authPassword = proxyConfig.url.userInfo?.password,
                        authType = if (proxyConfig.url.userInfo != null) HttpProxyAuthorizationType.Basic else HttpProxyAuthorizationType.None,
                    )
                    else -> null
                }
            }.build()
            HttpClientConnectionManager(connOpts)
        }
    }
    override fun close() {
        connManagers.forEach { entry -> entry.value.close() }
        crtTlsContext.close()
    }

    private inner class LeasedConnection(private val delegate: HttpClientConnection) : HttpClientConnection by delegate {
        override fun close() {
            try {
                // close actually returns to the pool
                delegate.close()
            } finally {
                leases.release()
            }
        }
    }
}

private fun toCrtTlsVersion(sdkTlsVersion: SdkTlsVersion?): CrtTlsVersion = when (sdkTlsVersion) {
    null -> aws.sdk.kotlin.crt.io.TlsVersion.SYS_DEFAULT
    TlsVersion.TLS_1_0 -> CrtTlsVersion.TLSv1
    TlsVersion.TLS_1_1 -> CrtTlsVersion.TLS_V1_1
    TlsVersion.TLS_1_2 -> CrtTlsVersion.TLS_V1_2
    TlsVersion.TLS_1_3 -> CrtTlsVersion.TLS_V1_3
}
