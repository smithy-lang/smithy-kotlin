/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.sdk.kotlin.crt.io.*
import aws.smithy.kotlin.runtime.InternalApi

// FIXME - this should default to number of processors
private const val DEFAULT_EVENT_LOOP_THREAD_COUNT: Int = 1

/**
 * Default (CRT) IO used by the SDK when not configured manually/directly
 */
@Deprecated("This API is no longer used by the SDK and will be removed in version 1.6.x")
@InternalApi
public object SdkDefaultIO {
    /**
     * The default event loop group to run IO on
     */
    @Deprecated("This API is no longer used by the SDK and will be removed in version 1.6.x")
    public val EventLoop: EventLoopGroup by lazy {
        EventLoopGroup(DEFAULT_EVENT_LOOP_THREAD_COUNT)
    }

    /**
     * The default host resolver to resolve DNS queries with
     */
    @Deprecated("This API is no longer used by the SDK and will be removed in version 1.6.x")
    public val HostResolver: HostResolver by lazy {
        @Suppress("DEPRECATION")
        HostResolver(EventLoop)
    }

    /**
     * The default client bootstrap
     */
    @Deprecated("This API is no longer used by the SDK and will be removed in version 1.6.x")
    public val ClientBootstrap: ClientBootstrap by lazy {
        @Suppress("DEPRECATION")
        ClientBootstrap(EventLoop, HostResolver)
    }

    /**
     * The default TLS context
     */
    @Deprecated("This API is no longer used by the SDK and will be removed in version 1.6.x")
    public val TlsContext: TlsContext by lazy {
        TlsContext(TlsContextOptions.defaultClient())
    }
}
