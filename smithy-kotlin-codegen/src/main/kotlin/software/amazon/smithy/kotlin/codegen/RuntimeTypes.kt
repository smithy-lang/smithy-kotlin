/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.Symbol

/**
 * Commonly used runtime types. Provides a single definition of a runtime symbol such that codegen isn't littered
 * with inline symbol creation which makes refactoring of the runtime more difficult and error prone.
 *
 * NOTE: Not all symbols need be added here but it doesn't hurt to define runtime symbols once.
 */
object RuntimeTypes {
    object Http {
        val HttpRequestBuilder = runtimeSymbol("HttpRequestBuilder", KotlinDependency.CLIENT_RT_HTTP)
        val HttpSerialize = runtimeSymbol("HttpSerialize", KotlinDependency.CLIENT_RT_HTTP, subpackage = "operation")
    }

    object Core {
        val IdempotencyTokenProviderExt = runtimeSymbol("idempotencyTokenProvider", KotlinDependency.CLIENT_RT_CORE, "client")
        val ExecutionContext = runtimeSymbol("ExecutionContext", KotlinDependency.CLIENT_RT_CORE, "client")
    }
}

private fun runtimeSymbol(name: String, dependency: KotlinDependency, subpackage: String = ""): Symbol = buildSymbol {
    this.name = name
    namespace(dependency, subpackage)
}
