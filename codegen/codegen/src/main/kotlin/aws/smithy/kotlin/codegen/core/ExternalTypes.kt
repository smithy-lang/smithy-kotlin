/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.core

import aws.smithy.kotlin.codegen.model.toSymbol

/**
 * Commonly used external types.
 */
object ExternalTypes {
    // https://github.com/Kotlin/kotlinx.coroutines
    object KotlinxCoroutines {
        val Flow = "kotlinx.coroutines.flow.Flow".toSymbol()
        val FlowGenerator = "kotlinx.coroutines.flow.flow".toSymbol()
        val FlowTransform = "kotlinx.coroutines.flow.transform".toSymbol()
    }
}
