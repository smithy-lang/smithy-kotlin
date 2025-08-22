/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.engine.okhttp

import javax.net.ssl.KeyManager

/**
 * Provider for TLS key managers used for client certificate authentication.
 *
 * Key managers provide the client's private key and certificate chain when
 * the server requests client authentication during TLS handshake.
 */
public interface TlsKeyManagersProvider {
    /**
     * @return The [KeyManager]s used for client authentication.
     */
    public fun keyManagers(): Array<KeyManager>
}
