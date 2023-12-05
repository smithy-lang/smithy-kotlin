/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import aws.smithy.kotlin.runtime.content.decodeToString
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.http.toByteStream
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.test.runTest
import java.io.File
import java.security.KeyStore
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CERTIFICATE_ALIAS = "certificate alias"
private const val CERTIFICATE_PASSWORD = "certificate pass"
private const val EXPECTED_REQUEST_BODY = "Do you accept self-signed certificates?"
private const val EXPECTED_RESPONSE_BODY = "I *do* accept self-signed certificates!"
private const val KEY_STORE_PASSWORD = "key store pass"
private const val LOCALHOST = "127.0.0.1"
private const val PATH = "/path/to/the/test"
private const val PORT = 8443

class CustomCertificateTest {
    @Test
    fun testCustomCertificate() = runTest {
        val (keyStore, keyStoreFile) = createKeyStore()
        val serverEnv = createServerEnv(keyStore, keyStoreFile)
        val server = embeddedServer(Netty, serverEnv).start()

        val request = HttpRequest {
            method = HttpMethod.POST
            url {
                scheme = Scheme.HTTPS
                host = Host.parse(LOCALHOST)
                port = PORT
                path.encoded = PATH
            }
            body = HttpBody.fromBytes(EXPECTED_REQUEST_BODY.toByteArray())
        }

        try {
            withProperties(
                "javax.net.ssl.trustStore" to keyStoreFile.absolutePath,
                "javax.net.ssl.trustStorePassword" to KEY_STORE_PASSWORD,
            ) {
                val engine = OkHttpEngine()
                val client = SdkHttpClient(engine)
                val call = client.call(request)
                val respBody = call.response.body.toByteStream()!!.decodeToString()
                assertEquals(EXPECTED_RESPONSE_BODY, respBody)
            }
        } finally {
            server.stop()
        }
    }

    private fun createKeyStore(): Pair<KeyStore, File> {
        val keyStoreFile = File.createTempFile("custom-ssl-keystore", ".jks")
        val keyStore = buildKeyStore {
            certificate(CERTIFICATE_ALIAS) {
                password = CERTIFICATE_PASSWORD
                domains = listOf(LOCALHOST)
            }
        }
        keyStore.saveToFile(keyStoreFile, KEY_STORE_PASSWORD)

        return keyStore to keyStoreFile
    }

    private fun createServerEnv(keyStore: KeyStore, keyStoreFile: File) = applicationEngineEnvironment {
        sslConnector(
            keyStore = keyStore,
            keyAlias = CERTIFICATE_ALIAS,
            keyStorePassword = KEY_STORE_PASSWORD::toCharArray,
            privateKeyPassword = CERTIFICATE_PASSWORD::toCharArray,
        ) {
            port = PORT
            keyStorePath = keyStoreFile
        }

        module {
            routing {
                post(PATH) {
                    val reqBody = call.receiveText()
                    assertEquals(EXPECTED_REQUEST_BODY, reqBody)
                    call.respondText(EXPECTED_RESPONSE_BODY)
                }
            }
        }
    }
}

private suspend fun withProperties(vararg properties: Pair<String, String>, block: suspend () -> Unit) {
    val oldValues = properties.associate { (name, newValue) -> name to System.setProperty(name, newValue) }
    try {
        block()
    } finally {
        oldValues.forEach { (name, oldValue) ->
            when (oldValue) {
                null -> System.clearProperty(name)
                else -> System.setProperty(name, oldValue)
            }
        }
    }
}
