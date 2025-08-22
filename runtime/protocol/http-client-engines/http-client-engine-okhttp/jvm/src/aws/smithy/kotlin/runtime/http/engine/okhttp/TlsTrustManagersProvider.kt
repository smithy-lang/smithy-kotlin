/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import javax.net.ssl.TrustManager

/**
 * Provider for TLS trust managers used to validate server certificates.
 *
 * Trust managers determine whether to trust the certificate chain presented
 * by a remote server during TLS handshake.
 */
public interface TlsTrustManagersProvider {
    /**
     * @return The [TrustManager]s used for certificate validation.
     */
    public fun trustManagers(): Array<TrustManager>
}
