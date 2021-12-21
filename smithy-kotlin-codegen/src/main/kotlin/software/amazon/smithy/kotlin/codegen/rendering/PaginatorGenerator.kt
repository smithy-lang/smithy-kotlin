/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.capitalizedDefaultName
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.SymbolProperty
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.toSymbol
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.kotlin.codegen.utils.toggleFirstCharacterCase
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.PaginatedIndex
import software.amazon.smithy.model.knowledge.PaginationInfo
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.PaginatedTrait

/**
 * Generate paginators for supporting operations.  See
 * https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#paginated-trait for details.
 */
class PaginatorGenerator : KotlinIntegration {
    private val kotlinxFlowSymbol = "kotlinx.coroutines.flow.Flow".toSymbol()
    private val kotlinxFlowGeneratorSymbol = "kotlinx.coroutines.flow.flow".toSymbol()
    private val kotlinxFlowMapSymbol = "kotlinx.coroutines.flow.map".toSymbol()
    private val kotlinxFlowTransformSymbol = "kotlinx.coroutines.flow.transform".toSymbol()

    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean =
        model.operationShapes.any { it.hasTrait<PaginatedTrait>() }

    override fun writeAdditionalFiles(ctx: CodegenContext, delegator: KotlinDelegator) {
        val service = ctx.model.expectShape<ServiceShape>(ctx.settings.service)
        val paginatedIndex = PaginatedIndex.of(ctx.model)

        delegator.useFileWriter("Paginators.kt", ctx.settings.pkg.name) { writer ->
            val paginatedOperations = service.allOperations
                .map { ctx.model.expectShape<OperationShape>(it) }
                .filter { operationShape -> operationShape.hasTrait(PaginatedTrait.ID) }

            paginatedOperations.forEach { paginatedOperation ->
                val paginationInfo = paginatedIndex.getPaginationInfo(service, paginatedOperation).getOrNull()
                    ?: throw CodegenException("Unexpectedly unable to get PaginationInfo from $service $paginatedOperation")
                val paginationItemInfo = getItemDescriptorOrNull(paginationInfo, ctx)

                renderPaginatorForOperation(writer, ctx, service, paginatedOperation, paginationInfo, paginationItemInfo)
            }
        }
    }

    // Render paginator(s) for operation
    private fun renderPaginatorForOperation(
        writer: KotlinWriter,
        ctx: CodegenContext,
        service: ServiceShape,
        paginatedOperation: OperationShape,
        paginationInfo: PaginationInfo,
        itemDesc: ItemDescriptor?
    ) {
        val serviceSymbol = ctx.symbolProvider.toSymbol(service)
        val outputSymbol = ctx.symbolProvider.toSymbol(paginationInfo.output)
        val inputSymbol = ctx.symbolProvider.toSymbol(paginationInfo.input)
        val cursorMember = ctx.model.getShape(paginationInfo.inputTokenMember.target).get()
        val cursorSymbol = ctx.symbolProvider.toSymbol(cursorMember)

        renderResponsePaginator(
            writer,
            serviceSymbol,
            paginatedOperation,
            inputSymbol,
            outputSymbol,
            paginationInfo,
            cursorSymbol
        )

        // Optionally generate paginator when nested item is specified on the trait.
        if (itemDesc != null) {
            renderItemPaginator(
                writer,
                service,
                itemDesc.itemLiteral,
                outputSymbol,
                itemDesc.itemSymbol,
                itemDesc.itemPathLiteral,
                itemDesc.targetMember,
                itemDesc.collectionLiteral
            )
        }
    }

    // Generate the paginator that iterates over responses
    private fun renderResponsePaginator(
        writer: KotlinWriter,
        serviceSymbol: Symbol,
        operationShape: OperationShape,
        inputSymbol: Symbol,
        outputSymbol: Symbol,
        paginationInfo: PaginationInfo,
        cursorSymbol: Symbol
    ) {
        val nextMarkerLiteral = paginationInfo.outputTokenMemberPath.joinToString(separator = "?.") {
            it.defaultName()
        }
        val markerLiteral = paginationInfo.inputTokenMember.defaultName()

        writer.write("")
        writer.dokka("Paginate over [${outputSymbol.name}]")
        writer
            .addImport(kotlinxFlowSymbol)
            .addImport(kotlinxFlowGeneratorSymbol)
            .addImport(kotlinxFlowMapSymbol)
            .addImport(inputSymbol)
            .addImport(outputSymbol)
            .addImport(cursorSymbol)
            .addImportReferences(cursorSymbol, SymbolReference.ContextOption.DECLARE)
            .withBlock(
                "fun #T.paginate#L(initialRequest: #T): Flow<#T> {",
                "}",
                serviceSymbol,
                operationShape.capitalizedDefaultName(),
                inputSymbol,
                outputSymbol
            ) {
                withBlock("return flow {", "}") {
                    write("var cursor: #F = null", cursorSymbol)
                    write("var isFirstPage: Boolean = true")
                    write("")
                    withBlock("while (isFirstPage || (cursor?.isNotEmpty() == true)) {", "}") {
                        withBlock("val req = initialRequest.copy {", "}") {
                            write("this.$markerLiteral = cursor")
                        }
                        write(
                            "val result = this@paginate#L.#L(req)",
                            operationShape.capitalizedDefaultName(),
                            operationShape.defaultName()
                        )
                        write("isFirstPage = false")
                        write("cursor = result.$nextMarkerLiteral")
                        write("emit(result)")
                    }
                }
            }
    }

    // Generate a paginator that iterates over the model-specified item
    private fun renderItemPaginator(
        writer: KotlinWriter,
        serviceShape: ServiceShape,
        itemLiteral: String,
        outputSymbol: Symbol,
        paginatedTypeSymbol: Symbol,
        itemPathLiteral: String,
        targetMember: Shape,
        targetTypeLiteral: String
    ) {
        writer.write("")
        writer.dokka("Paginate over [${outputSymbol.name}.$itemLiteral]")
        writer
            .addImport(kotlinxFlowMapSymbol)
            .addImport(kotlinxFlowTransformSymbol)
            .addImport(paginatedTypeSymbol)
            .addImportReferences(paginatedTypeSymbol, SymbolReference.ContextOption.USE)
            .write(
                """@JvmName("#L#L")""",
                outputSymbol.name.toggleFirstCharacterCase(),
                targetMember.defaultName(serviceShape)
            )
            .withBlock(
                "fun #T<#T>.on#L(): #T<#L> =", "",
                kotlinxFlowSymbol,
                outputSymbol,
                targetMember.defaultName(serviceShape),
                kotlinxFlowSymbol,
                targetTypeLiteral
            ) {
                withBlock("transform() { response -> ", "}") {
                    withBlock("response.#L?.forEach {", "}", itemPathLiteral) {
                        write("emit(it)")
                    }
                }
            }
    }
}

/**
 * Model info necessary to codegen paginator item
 */
data class ItemDescriptor(
    val collectionLiteral: String,
    val targetMember: Shape,
    val itemLiteral: String,
    val itemPathLiteral: String,
    val itemSymbol: Symbol
)

/**
 * Return an [ItemDescriptor] if model supplies, otherwise null
 */
private fun getItemDescriptorOrNull(paginationInfo: PaginationInfo, ctx: CodegenContext): ItemDescriptor? {
    val itemMemberId = paginationInfo.itemsMemberPath?.lastOrNull()?.target ?: return null

    val itemLiteral = paginationInfo.itemsMemberPath!!.last()!!.defaultName()
    val itemPathLiteral = paginationInfo.itemsMemberPath.joinToString(separator = "?.") { it.defaultName() }
    val itemMember = ctx.model.expectShape(itemMemberId)
    val (collectionLiteral, targetMember) = when (itemMember) {
        is MapShape ->
            ctx.symbolProvider.toSymbol(itemMember)
                .expectProperty(SymbolProperty.ENTRY_EXPRESSION) as String to itemMember
        is CollectionShape ->
            ctx.symbolProvider.toSymbol(ctx.model.expectShape(itemMember.member.target)).name to ctx.model.expectShape(
                itemMember.member.target
            )
        else -> error("Unexpected shape type ${itemMember.type}")
    }

    return ItemDescriptor(
        collectionLiteral,
        targetMember,
        itemLiteral,
        itemPathLiteral,
        ctx.symbolProvider.toSymbol(itemMember)
    )
}
