/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net

/**
 * Describes a version of TLS
 */
public enum class TlsVersion {
    /**
     * TLS version 1
     */
    TLS_1_0,

    /**
     * TLS version 1.1
     */
    TLS_1_1,

    /**
     * TLS version 1.2
     */
    TLS_1_2,

    /**
     * TLS version 1.3
     */
    TLS_1_3,
}
