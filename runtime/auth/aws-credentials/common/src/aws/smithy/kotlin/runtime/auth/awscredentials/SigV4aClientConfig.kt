/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

/**
 * The configuration properties for a client that supports SigV4a
 */
public interface SigV4aClientConfig {
    /**
     * The set of regions to use when signing a request with SigV4a.
     */
    public val sigV4aSigningRegionSet: Set<String>?

    public interface Builder {
        /**
         * The set of regions to use when signing a request with SigV4a.
         */
        public var sigV4aSigningRegionSet: Set<String>?
    }
}
