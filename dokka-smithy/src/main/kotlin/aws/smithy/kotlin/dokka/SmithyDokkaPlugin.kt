/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.dokka

import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin

/**
 * Dokka plugin for customizing the Smithy Kotlin SDK generated API docs
 */
class SmithyDokkaPlugin : DokkaPlugin() {
    init {
        println("${this.javaClass.canonicalName} loaded!")
    }

    val dokkaBase by lazy { plugin<DokkaBase>() }

    val filterInternalApis by extending {
        dokkaBase.preMergeDocumentableTransformer providing ::FilterInternalApis
    }
}
