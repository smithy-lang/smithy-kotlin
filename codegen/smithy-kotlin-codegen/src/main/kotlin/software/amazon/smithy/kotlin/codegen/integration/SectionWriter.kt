/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter

/**
 * A tag interface to denote a unique point in codegen in which integrations
 * may change or override codegen output.  The id is derived from the unique
 * namespace of the concrete child type.
 */
interface SectionId

data class SectionKey<T>(val name: String)

/**
 * A [SectionWriter] integrates with Smithy Sections.  It takes in a
 * [KotlinWriter] and default codegen strings (if any).  Implementations
 * use the [KotlinWriter] to inject code at the specified section.
 */
fun interface SectionWriter {
    /**
     * This function writes codegen for the bound section.
     * @param writer associated w/ file in which section content is emitted to
     * @param previousValue any codegen output provided by the base implementation or a previously
     *  evaluated [SectionWriter] associated with the same [SectionId]. For writers that wish to
     *  append to any pre-existing codegen strings in the section, they must explicitly write
     *  the contents of previousValue to the writer.  See the
     *  [CodeWriter](https://github.com/awslabs/smithy/blob/main/smithy-utils/src/main/java/software/amazon/smithy/utils/CodeWriter.java)
     *  documentation for more details
     */
    fun write(writer: KotlinWriter, previousValue: String?)
}

/**
 * A [SectionWriter] that always appends to the existing section contents (if any).
 */
fun interface AppendingSectionWriter : SectionWriter {
    override fun write(writer: KotlinWriter, previousValue: String?) {
        if (!previousValue.isNullOrBlank()) {
            writer.write(previousValue)
        }
        append(writer)
    }

    /**
     * This function writes code for the bound section.
     * @param writer the writer used to write contents to for the active section, contents are always appended
     * in the case of multiple section writers being bound to the same section.
     */
    fun append(writer: KotlinWriter)
}

/**
 * Binds a [SectionId] to a specific [SectionWriter]. Integrations may provide
 * lists of these bindings to allow overriding of codegen output at defined points
 * in where [CodeWriter.markSection()] has been called.
 *
 * In order to implement a [SectionWriter] via an integration:
 * 1. In the codegen code where output may be changed by integrations, register the section
 *    via the [CodeWriter.declareSection] function. Define a [SectionId] as a child member
 *    of the type responsible for housing the section.
 * 2. In the [KotlinIntegration], override the [KotlinIntegration.sectionWriters] and supply
 *    one or more instances of [SectionWriterBinding].  Supply the [SectionId] defined in step
 *    1 and provide an implementation of [SectionWriter] which will add or mutate the codegen
 *    associated with the section.
 */
data class SectionWriterBinding(val sectionId: SectionId, val emitter: SectionWriter)
