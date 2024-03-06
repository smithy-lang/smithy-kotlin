/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.config

/**
 * The configuration properties for a client that supports SigV4a
 */
public interface SigV4aClientConfig {
    /**
     * The set of regions to use when signing a request with SigV4a. If provided it will override endpoints metadata.
     */
    public val sigV4aSigningRegionSet: Set<String>?

    public interface Builder {
        /**
         * The set of regions to use when signing a request with SigV4a. If provided it will override endpoints metadata.
         */
        public var sigV4aSigningRegionSet: Set<String>?
    }
}
