/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

private const val EmitCompiledSources: String = "software.amazon.smithy.kotlin.codegen.compile.emitSourcesToTemp"

object Debug{
    // Toggle this flag to emit generated SDKs to /tmp for interactive debugging.
    val emitSourcesToTemp: Boolean
        get() {
            val props = System.getProperties()
            return props.containsKey(EmitCompiledSources) && props.getProperty(EmitCompiledSources).toBoolean()
        }
}
