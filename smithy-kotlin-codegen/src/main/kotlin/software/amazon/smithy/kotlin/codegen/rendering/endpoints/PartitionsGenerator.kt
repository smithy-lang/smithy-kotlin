/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.node.ObjectNode

/**
 * Generates the list of service partitions from the partitions.json resource.
 */
class PartitionsGenerator(
    private val writer: KotlinWriter,
    private val partitionsSpec: ObjectNode,
) {
    companion object {
        fun getSymbol(ctx: CodegenContext): Symbol =
            buildSymbol {
                name = "Partitions"
                namespace = "${ctx.settings.pkg.name}.endpoints"
            }
    }

    fun render() {
        renderPartitionFn()
        writer.write("")
        renderDefaultPartitions()
    }

    private fun renderPartitionFn() {
        writer.withBlock(
            "public fun partition(region: #T?): #T? {",
            "}",
            KotlinTypes.String,
            RuntimeTypes.Http.Endpoints.Functions.PartitionConfig,
        ) {
            write("return #T(defaultPartitions, region)", RuntimeTypes.Http.Endpoints.Functions.partitionFn)
        }
    }

    private fun renderDefaultPartitions() {
        writer.withBlock("private val defaultPartitions = listOf(", ")") {
            val partitions = partitionsSpec.expectArrayMember("partitions")

            partitions.elements.forEach {
                renderPartition(it.expectObjectNode())
            }
        }
    }

    private fun renderPartition(partition: ObjectNode) {
        val baseConfig = partition.expectObjectMember("outputs")

        writer.withBlock("#T(", "),", RuntimeTypes.Http.Endpoints.Functions.Partition) {
            write("id = #S,", partition.expectStringMember("id").value)
            write("regionRegex = Regex(#S),", partition.expectStringMember("regionRegex").value)
            withBlock("regions = mapOf(", "),") {
                partition.expectObjectMember("regions").stringMap.entries.forEach { (k, v) ->
                    val regionConfig = v.expectObjectNode()

                    withBlock("#S to #T(", "),", k, RuntimeTypes.Http.Endpoints.Functions.PartitionConfig) {
                        regionConfig.getStringMember("name").getOrNull()?.let {
                            write("name = #S,", it.value)
                        }
                        regionConfig.getStringMember("dnsSuffix").getOrNull()?.let {
                            write("dnsSuffix = #S,", it.value)
                        }
                        regionConfig.getStringMember("dualStackDnsSuffix").getOrNull()?.let {
                            write("dualStackDnsSuffix = #S,", it.value)
                        }
                        regionConfig.getBooleanMember("supportsFIPS").getOrNull()?.let {
                            write("supportsFIPS = #L,", it.value)
                        }
                        regionConfig.getBooleanMember("supportsDualStack").getOrNull()?.let {
                            write("supportsDualStack = #L,", it.value)
                        }
                    }
                }
            }
            withBlock("baseConfig = #T(", "),", RuntimeTypes.Http.Endpoints.Functions.PartitionConfig) {
                write("name = #S,", baseConfig.expectStringMember("name").value)
                write("dnsSuffix = #S,", baseConfig.expectStringMember("dnsSuffix").value)
                write("dualStackDnsSuffix = #S,", baseConfig.expectStringMember("dualStackDnsSuffix").value)
                write("supportsFIPS = #L,", baseConfig.expectBooleanMember("supportsFIPS").value)
                write("supportsDualStack = #L,", baseConfig.expectBooleanMember("supportsDualStack").value)
            }
        }
    }
}
