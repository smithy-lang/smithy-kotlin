/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.dsl

import software.amazon.smithy.model.node.ToNode

/**
 * Represents settings related to a Smithy plugin
 */
interface SmithyBuildPluginSettings : ToNode {
    /**
     * The name of the build plugin (e.g. `kotlin-codegen`). This is used when generating
     * the projection settings for the plugin
     */
    val pluginName: String
}
