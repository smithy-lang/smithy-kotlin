/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.dokka

import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement

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

    // FIXME Re-enable search once Dokka addresses performance issues
    // https://github.com/Kotlin/dokka/issues/2741
    val disableSearch by extending {
        dokkaBase.htmlPreprocessors providing ::NoOpSearchbarDataInstaller override dokkaBase.baseSearchbarDataInstaller
    }

    @OptIn(DokkaPluginApiPreview::class)
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement
}
