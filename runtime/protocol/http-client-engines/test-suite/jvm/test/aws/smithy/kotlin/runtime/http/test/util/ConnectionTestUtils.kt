
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.test.util

import aws.smithy.kotlin.runtime.http.engine.okhttp.TlsTrustManagersProvider
import aws.smithy.kotlin.runtime.net.toUrlString
import okhttp3.CertificatePinner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger.valueOf
import java.nio.file.Paths
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

internal val testSslConfig by lazy {
    val sslConfigPath = System.getProperty("SSL_CONFIG_PATH")
    SslConfig.load(Paths.get(sslConfigPath))
}

internal val testCert by lazy {
    testSslConfig.keyStore.getCertificate(testSslConfig.certificateAlias) as X509Certificate
}

fun createTestTrustManagerProvider(testCert: X509Certificate): TlsTrustManagersProvider =
    object : TlsTrustManagersProvider {
        override fun trustManagers(): Array<TrustManager> {
            val keyStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("test-cert", testCert)

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)
            return trustManagerFactory.trustManagers
        }
    }

fun createTestCertificatePinner(testCert: X509Certificate, serverType: ServerType): CertificatePinner {
    val sha256Hash = java.security.MessageDigest.getInstance("SHA-256").digest(testCert.publicKey.encoded)
    val pin = "sha256/" + Base64.getEncoder().encodeToString(sha256Hash)
    val hostname = testServers.getValue(serverType).host.toUrlString()

    return CertificatePinner.Builder()
        .add(hostname, pin)
        .build()
}

fun createTestPemCert(testCert: X509Certificate): String =
    """
    -----BEGIN CERTIFICATE-----
    ${Base64.getEncoder().encodeToString(testCert.encoded)}
    -----END CERTIFICATE-----
    """.trimIndent()

fun createInvalidTestPemCert(): String {
    // Generate an invalid certificate PEM
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()

    val subject = X500Name("CN=invalid.example.com")
    val serial = valueOf(System.currentTimeMillis())
    val notBefore = Date()
    val notAfter = Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000L) // 1 year

    val certBuilder = JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.public)

    val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
    val cert = certBuilder.build(signer)

    return """
        -----BEGIN CERTIFICATE-----
        ${Base64.getEncoder().encodeToString(cert.encoded).chunked(64).joinToString("\n    ")}
        -----END CERTIFICATE-----
    """.trimIndent()
}
