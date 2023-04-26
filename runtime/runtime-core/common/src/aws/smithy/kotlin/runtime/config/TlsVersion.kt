/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.config

/**
 * Describes a version of TLS
 */
@Suppress("ktlint:enum-entry-name-case")
public enum class TlsVersion {
    /**
     * TLS version 1
     */
    Tls1_0,

    /**
     * TLS version 1.1
     */
    Tls1_1,

    /**
     * TLS version 1.2
     */
    Tls1_2,

    /**
     * TLS version 1.3
     */
    Tls1_3,
}
