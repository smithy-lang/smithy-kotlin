/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.util

import io.ktor.network.tls.certificates.*
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore

private const val DEFAULT_CERTIFICATE_ALIAS = "certificate alias"
private const val DEFAULT_CERTIFICATE_PASSWORD = "certificate pass"
private val DEFAULT_HOSTS = listOf("localhost", "127.0.0.1")
private const val DEFAULT_KEY_STORE_PASSWORD = "key store pass"

internal data class SslConfig private constructor(
    val keyStore: KeyStore,
    val keyStoreFile: File,
    val keyStorePassword: String,
    val certificateAlias: String,
    val certificatePassword: String,
    val hosts: List<String>,
) {
    companion object {
        fun generate(
            keyStorePassword: String = DEFAULT_KEY_STORE_PASSWORD,
            certificateAlias: String = DEFAULT_CERTIFICATE_ALIAS,
            certificatePassword: String = DEFAULT_CERTIFICATE_PASSWORD,
            hosts: List<String> = DEFAULT_HOSTS,
        ): SslConfig {
            val file = File.createTempFile("custom-ssl-keystore-", ".jks")
            val keyStore = buildKeyStore {
                certificate(certificateAlias) {
                    password = certificatePassword
                    domains = hosts
                }
            }
            keyStore.saveToFile(file, keyStorePassword)

            return SslConfig(keyStore, file, keyStorePassword, certificateAlias, certificatePassword, hosts)
        }

        fun load(fromPath: Path): SslConfig {
            val text = Files.readString(fromPath)
            val (
                keyStoreFilePath,
                keyStorePassword,
                certificateAlias,
                certificatePassword,
                hostsList,
            ) = text.split("|")

            val keyStore = KeyStore.getInstance("JKS")
            keyStore.load(FileInputStream(keyStoreFilePath), keyStorePassword.toCharArray())

            return SslConfig(
                keyStore,
                File(keyStoreFilePath),
                keyStorePassword,
                certificateAlias,
                certificatePassword,
                hostsList.split(","),
            )
        }
    }

    fun persist(toPath: Path) {
        Files.writeString(toPath, toString())
    }

    override fun toString(): String =
        listOf(
            keyStoreFile.absolutePath,
            keyStorePassword,
            certificateAlias,
            certificatePassword,
            hosts.joinToString(","),
        ).joinToString("|")

    inline fun useAsSystemProperties(crossinline block: () -> Unit) {
        withProperties(
            "javax.net.ssl.trustStore" to keyStoreFile.absolutePath,
            "javax.net.ssl.trustStorePassword" to keyStorePassword,
            "jdk.tls.client.protocols" to "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3",
            "https.protocols" to "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3",
        ) {
            block()
        }
    }
}

private inline fun withProperties(vararg properties: Pair<String, String>, crossinline block: () -> Unit) {
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
