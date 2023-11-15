/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.codegen.core.*
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.lang.kotlinReservedWords
import software.amazon.smithy.kotlin.codegen.model.*
import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.NullableIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.DefaultTrait
import software.amazon.smithy.model.traits.StreamingTrait
import java.util.logging.Logger
import kotlin.math.round

/**
 * Convert shapes to Kotlin types
 * @param model The smithy model to generate for
 * @param settings [KotlinSettings] associated with this codegen
 */
class KotlinSymbolProvider(private val model: Model, private val settings: KotlinSettings) :
    SymbolProvider,
    ShapeVisitor<Symbol> {
    private val rootNamespace = settings.pkg.name
    private val service = model.expectShape<ServiceShape>(settings.service)
    private val logger = Logger.getLogger(javaClass.name)
    private val escaper: ReservedWordSymbolProvider.Escaper
    private val nullableIndex = NullableIndex(model)

    // model depth; some shapes use `toSymbol()` internally as they convert (e.g.) member shapes to symbols, this tracks
    // how deep in the model we have recursed
    private var depth = 0

    init {
        val reservedWords = kotlinReservedWords()
        escaper = ReservedWordSymbolProvider.builder()
            .nameReservedWords(reservedWords)
            .memberReservedWords(reservedWords)
            // only escape words when the symbol has a definition file to prevent escaping intentional references to built-in shapes
            .escapePredicate { _, symbol -> symbol.definitionFile.isNotEmpty() }
            .buildEscaper()
    }

    companion object {
        /**
         * Determines if a new Kotlin type is generated for a given shape. Generally only structures, unions, and enums
         * result in a type being generated. Strings, ints, etc are mapped to builtins
         */
        fun isTypeGeneratedForShape(shape: Shape): Boolean = when {
            // pretty much anything we visit in CodegenVisitor (we don't care about service shape here though)
            shape.isEnum || shape.isStructureShape || shape.isUnionShape -> true
            else -> false
        }
    }

    override fun toSymbol(shape: Shape): Symbol {
        depth++
        val symbol: Symbol = shape.accept(this)
        depth--
        logger.fine("creating symbol from $shape: $symbol")
        return escaper.escapeSymbol(shape, symbol)
    }

    override fun toMemberName(shape: MemberShape): String = escaper.escapeMemberName(shape.defaultName())

    override fun byteShape(shape: ByteShape): Symbol = numberShape(shape, "Byte")

    override fun integerShape(shape: IntegerShape): Symbol = numberShape(shape, "Int")

    override fun intEnumShape(shape: IntEnumShape): Symbol = createEnumSymbol(shape)

    override fun shortShape(shape: ShortShape): Symbol = numberShape(shape, "Short")

    override fun longShape(shape: LongShape): Symbol = numberShape(shape, "Long")

    override fun floatShape(shape: FloatShape): Symbol = numberShape(shape, "Float")

    override fun doubleShape(shape: DoubleShape): Symbol = numberShape(shape, "Double")

    private fun numberShape(shape: Shape, typeName: String): Symbol =
        createSymbolBuilder(shape, typeName, namespace = "kotlin").build()

    // strip nullability from these runtime symbols as nullability is context dependent
    override fun bigIntegerShape(shape: BigIntegerShape): Symbol = RuntimeTypes.Core.Content.BigInteger.asNonNullable()

    override fun bigDecimalShape(shape: BigDecimalShape): Symbol = RuntimeTypes.Core.Content.BigDecimal.asNonNullable()

    override fun stringShape(shape: StringShape): Symbol = if (shape.isEnum) {
        createEnumSymbol(shape)
    } else {
        createSymbolBuilder(shape, "String", namespace = "kotlin").build()
    }

    private fun createEnumSymbol(shape: Shape): Symbol {
        val namespace = "$rootNamespace.model"
        return createSymbolBuilder(shape, shape.defaultName(service), namespace)
            .definitionFile("${shape.defaultName(service)}.kt")
            .build()
    }

    override fun booleanShape(shape: BooleanShape?): Symbol =
        createSymbolBuilder(shape, "Boolean", namespace = "kotlin").build()

    override fun structureShape(shape: StructureShape): Symbol {
        val name = shape.defaultName(service)
        val namespace = "$rootNamespace.model"
        val builder = createSymbolBuilder(shape, name, namespace)
            .definitionFile("$name.kt")

        // add a reference to each member symbol
        addDeclareMemberReferences(builder, shape.allMembers.values)

        return builder.build()
    }

    /**
     * Add all the [members] as references needed to declare the given symbol being built.
     */
    private fun addDeclareMemberReferences(builder: Symbol.Builder, members: Collection<MemberShape>) {
        // when converting a shape to a symbol we only need references to top level members
        // in order to declare the symbol. This prevents recursive shapes from causing a stack overflow (and doing
        // unnecessary work since we don't need the inner references)
        if (depth > 1) return
        members.forEach {
            val memberSymbol = toSymbol(it)
            builder.addReference(memberSymbol, SymbolReference.ContextOption.DECLARE)

            when (model.expectShape(it.target)) {
                // collections and maps may have a value type that needs a reference
                is CollectionShape, is MapShape -> addSymbolReferences(memberSymbol, builder)
            }
        }
    }

    private fun addSymbolReferences(from: Symbol, to: Symbol.Builder) {
        if (from.references.isEmpty()) return
        from.references.forEach {
            addSymbolReferences(it.symbol, to)
            to.addReference(it)
        }
    }

    override fun listShape(shape: ListShape): Symbol {
        val reference = toSymbol(shape.member)
        val valueSuffix = if (reference.isNullable) "?" else ""
        val valueType = "${reference.name}$valueSuffix"
        val fullyQualifiedValueType = "${reference.fullName}$valueSuffix"
        return createSymbolBuilder(shape, "List<$valueType>")
            .addReferences(reference)
            .putProperty(SymbolProperty.FULLY_QUALIFIED_NAME_HINT, "List<$fullyQualifiedValueType>")
            .putProperty(SymbolProperty.MUTABLE_COLLECTION_FUNCTION, "mutableListOf<$valueType>")
            .putProperty(SymbolProperty.IMMUTABLE_COLLECTION_FUNCTION, "listOf<$valueType>")
            .build()
    }

    override fun mapShape(shape: MapShape): Symbol {
        val reference = toSymbol(shape.value)
        val valueSuffix = if (reference.isNullable) "?" else ""
        val valueType = "${reference.name}$valueSuffix"
        val fullyQualifiedValueType = "${reference.fullName}$valueSuffix"

        val keyType = KotlinTypes.String.name
        val fullyQualifiedKeyType = KotlinTypes.String.fullName
        return createSymbolBuilder(shape, "Map<$keyType, $valueType>")
            .addReferences(reference)
            .putProperty(SymbolProperty.FULLY_QUALIFIED_NAME_HINT, "Map<$fullyQualifiedKeyType, $fullyQualifiedValueType>")
            .putProperty(SymbolProperty.MUTABLE_COLLECTION_FUNCTION, "mutableMapOf<$keyType, $valueType>")
            .putProperty(SymbolProperty.IMMUTABLE_COLLECTION_FUNCTION, "mapOf<$keyType, $valueType>")
            .putProperty(SymbolProperty.ENTRY_EXPRESSION, "Map.Entry<$keyType, $valueType>")
            .build()
    }

    override fun memberShape(shape: MemberShape): Symbol {
        val targetShape =
            model.getShape(shape.target).orElseThrow { CodegenException("Shape not found: ${shape.target}") }

        val targetSymbol = toSymbol(targetShape)
            .toBuilder()
            .apply {
                val isNullable = nullableIndex.isMemberNullable(shape, settings.api.nullabilityCheckMode)
                if (isNullable) {
                    nullable()
                } else {
                    // only use @default if type is `T`
                    shape.getTrait<DefaultTrait>()?.let {
                        defaultValue(it.getDefaultValue(targetShape))
                    }
                }
            }
            .build()

        // figure out if we are referencing an event stream or not.
        // NOTE: unlike blob streams we actually re-use the target (union) shape which is why we can't do this
        // when visiting a unionShape() like we can for blobShape()
        val container = model.expectShape(shape.container) as? StructureShape
        val isOperationInputOrOutput = container != null && (container.isOperationInput || container.isOperationOutput)
        val isEventStream = targetShape.isStreaming && targetShape.isUnionShape

        return if (isOperationInputOrOutput && isEventStream) {
            // a top level operation input/output member referencing a streaming union is represented by a Flow<T>
            buildSymbol {
                name = "Flow<${targetSymbol.fullName}>"
                nullable = true
                reference(targetSymbol, SymbolReference.ContextOption.DECLARE)
                reference(RuntimeTypes.KotlinxCoroutines.Flow.Flow, SymbolReference.ContextOption.DECLARE)
            }
        } else {
            targetSymbol
        }
    }

    private fun DefaultTrait.getDefaultValue(targetShape: Shape): String? {
        val node = toNode()
        return when {
            node.toString() == "null" || targetShape is BlobShape && node.toString() == "" -> null

            // Check if target is an enum before treating the default like a regular number/string
            targetShape.isEnum -> {
                val enumSymbol = toSymbol(targetShape)
                val arg = when {
                    targetShape.isStringShape -> node.toString().dq()
                    targetShape.isIntEnumShape -> getDefaultValueForNumber(ShapeType.INTEGER, node.toString())
                    else -> throw CodegenException("Unknown enum type for $targetShape")
                }
                "${enumSymbol.fullName}.fromValue($arg)"
            }

            targetShape.isBlobShape -> "${node.toString().dq()}.encodeToByteArray()"
            targetShape.isDocumentShape -> getDefaultValueForDocument(node)
            targetShape.isTimestampShape -> getDefaultValueForTimestamp(node.asNumberNode().get())

            node.isNumberNode -> getDefaultValueForNumber(targetShape.type, node.toString())
            node.isArrayNode -> "listOf()"
            node.isObjectNode -> "mapOf()"
            node.isStringNode -> node.toString().dq()
            else -> node.toString()
        }
    }

    private fun getDefaultValueForTimestamp(node: NumberNode): String {
        val instant = RuntimeTypes.Core.Instant

        return if (node.isFloatingPointNumber) {
            val fromEpochMilliseconds = RuntimeTypes.Core.fromEpochMilliseconds // FIXME how to import this without access to a writer?
            val value = node.value as Double
            val ms = round(value * 1e3).toLong()
            "$instant.$fromEpochMilliseconds($ms)"
        } else {
            "$instant.fromEpochSeconds(${node.value}, 0)"
        }
    }

    private fun getDefaultValueForDocument(node: Node): String {
        val documentSymbol = RuntimeTypes.Core.Content.Document.fullName
        val content: String = when {
            node.isArrayNode -> {
                val formattedElements: String = node.asArrayNode().get().elements.joinToString()
                "listOf($formattedElements)"
            }
            node.isObjectNode -> {
                val members = node.asObjectNode().get().members
                val formattedMembers: String = members.map { "${it.key.value} to ${it.value}" }.joinToString()
                "mapOf($formattedMembers)"
            }
            node.isNumberNode -> node.asNumberNode().get().value.toString()
            node.isStringNode -> node.asStringNode().get().value.dq()
            node.isBooleanNode -> node.asBooleanNode().get().value.toString()
            node.isNullNode -> "null"
            else -> throw RuntimeException("Unsupported node $node")
        }

        return "$documentSymbol($content)"
    }

    override fun timestampShape(shape: TimestampShape?): Symbol {
        val dependency = KotlinDependency.CORE
        return createSymbolBuilder(shape, "Instant")
            .namespace("${dependency.namespace}.time", ".")
            .addDependency(dependency)
            .build()
    }

    override fun blobShape(shape: BlobShape): Symbol = if (shape.hasTrait<StreamingTrait>()) {
        RuntimeTypes.Core.Content.ByteStream
    } else {
        createSymbolBuilder(shape, "ByteArray", namespace = "kotlin").build()
    }

    override fun documentShape(shape: DocumentShape?): Symbol =
        RuntimeTypes.Core.Content.Document.asNullable()

    override fun unionShape(shape: UnionShape): Symbol {
        val name = shape.defaultName(service)
        val namespace = "$rootNamespace.model"
        val builder = createSymbolBuilder(shape, name, namespace)
            .definitionFile("$name.kt")

        // add a reference to each member symbol
        addDeclareMemberReferences(builder, shape.allMembers.values)

        return builder.build()
    }

    override fun resourceShape(shape: ResourceShape?): Symbol {
        // The Kotlin SDK does not produce code explicitly based on Resources
        error { "unexpected codegen code path" }
    }

    override fun operationShape(shape: OperationShape?): Symbol {
        // The Kotlin SDK does not produce code explicitly based on Operations
        error { "Unexpected codegen code path" }
    }

    override fun serviceShape(shape: ServiceShape): Symbol {
        val serviceName = clientName(settings.sdkId)
        return createSymbolBuilder(shape, "${serviceName}Client")
            .namespace(rootNamespace, ".")
            .definitionFile("${serviceName}Client.kt").build()
    }

    /**
     * Creates a symbol builder for the shape with the given type name in the root namespace.
     */
    private fun createSymbolBuilder(shape: Shape?, typeName: String): Symbol.Builder =
        Symbol.builder()
            .putProperty(SymbolProperty.SHAPE_KEY, shape)
            .name(typeName)

    private fun getDefaultValueForNumber(type: ShapeType, value: String) = when (type) {
        ShapeType.LONG -> "${value}L"
        ShapeType.FLOAT -> "${value}f"
        ShapeType.DOUBLE -> if (value.matches("[0-9]*\\.[0-9]+".toRegex())) value else "$value.0"
        ShapeType.SHORT -> "$value.toShort()"
        ShapeType.BYTE -> "$value.toByte()"
        else -> value
    }

    /**
     * Creates a symbol builder for the shape with the given type name in a child namespace relative
     * to the root namespace e.g. `relativeNamespace = bar` with a root namespace of `foo` would set
     * the namespace (and ultimately the package name) to `foo.bar` for the symbol.
     */
    private fun createSymbolBuilder(
        shape: Shape?,
        typeName: String,
        namespace: String,
    ): Symbol.Builder = createSymbolBuilder(shape, typeName).namespace(namespace, ".")
}

// Add a reference and it's children
private fun Symbol.Builder.addReferences(ref: Symbol): Symbol.Builder {
    addReference(ref)
    ref.references.forEach { addReference(it) }
    return this
}
