/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.kotlinReservedWords
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.BoxTrait
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.StreamingTrait
import software.amazon.smithy.utils.StringUtils
import java.util.logging.Logger

// PropertyBag keys

// The key that holds the default value for a type (symbol) as a string
private const val DEFAULT_VALUE_KEY: String = "defaultValue"

// Boolean property indicating this symbol should be boxed
private const val BOXED_KEY: String = "boxed"

// the original shape the symbol was created from
private const val SHAPE_KEY: String = "shape"

/**
 * Test if a symbol is boxed or not
 */
fun Symbol.isBoxed(): Boolean {
    return getProperty(BOXED_KEY).map {
        when (it) {
            is Boolean -> it
            else -> false
        }
    }.orElse(false)
}

/**
 * Gets the default value for the symbol if present, else null
 * @param defaultBoxed the string to pass back for boxed values
 */
fun Symbol.defaultValue(defaultBoxed: String? = "null"): String? {
    // boxed types should always be defaulted to null
    if (isBoxed()) {
        return defaultBoxed
    }

    val default = getProperty(DEFAULT_VALUE_KEY, String::class.java)
    return if (default.isPresent) default.get() else null
}

/**
 * Get the default name for a shape (for code generation)
 */
fun Shape.defaultName(): String = StringUtils.capitalize(this.id.name)

/**
 * Get the default name for a member shape (for code generation)
 */
fun MemberShape.defaultName(): String = StringUtils.uncapitalize(this.memberName)

/**
 * Get the default name for an operation shape
 */
fun OperationShape.defaultName(): String = StringUtils.uncapitalize(this.id.name)

/**
 * Convert shapes to Kotlin types
 * @param model The smithy model to generate for
 * @param rootNamespace All symbols will be created under this namespace (package) or as a direct child of it.
 * e.g. `com.foo` would create symbols under the `com.foo` package or `com.foo.model` package, etc.
 * @param sdkId name to use to represent client type.  e.g. an sdkId of "foo" would produce a client type "FooClient".
 */
class SymbolVisitor(private val model: Model, private val rootNamespace: String = "", private val sdkId: String) :
    SymbolProvider,
    ShapeVisitor<Symbol> {
    private val logger = Logger.getLogger(javaClass.name)
    private val escaper: ReservedWordSymbolProvider.Escaper

    // model depth; some shapes use `toSymbol()` internally as they convert (e.g.) member shapes to symbols, this tracks
    // how deep in the model we have recursed
    private var depth = 0

    init {
        val reservedWords = kotlinReservedWords()
        escaper = ReservedWordSymbolProvider.builder()
            .nameReservedWords(reservedWords)
            .memberReservedWords(reservedWords)
            // only escape words when the symbol has a definition file to prevent escaping intentional references to built-in shapes
            .escapePredicate { _, symbol -> !symbol.definitionFile.isEmpty() }
            .buildEscaper()
    }

    companion object {
        // FIXME - Refactor by providing unified way of dealing w/ symbol metadata. https://www.pivotaltracker.com/story/show/176122163
        // Mutable collection type
        const val MUTABLE_COLLECTION_FUNCTION: String = "mutableCollectionType"
        const val IMMUTABLE_COLLECTION_FUNCTION: String = "immutableCollectionType"
    }

    override fun toSymbol(shape: Shape): Symbol {
        depth++
        val symbol: Symbol = shape.accept(this)
        depth--
        logger.fine("creating symbol from $shape: $symbol")
        return escaper.escapeSymbol(shape, symbol)
    }

    override fun toMemberName(shape: MemberShape): String {
        return escaper.escapeMemberName(shape.defaultName())
    }

    override fun byteShape(shape: ByteShape): Symbol = numberShape(shape, "Byte")

    override fun integerShape(shape: IntegerShape): Symbol = numberShape(shape, "Int")

    override fun shortShape(shape: ShortShape): Symbol = numberShape(shape, "Short")

    override fun longShape(shape: LongShape): Symbol = numberShape(shape, "Long")

    override fun floatShape(shape: FloatShape): Symbol = numberShape(shape, "Float", "0.0f")

    override fun doubleShape(shape: DoubleShape): Symbol = numberShape(shape, "Double", "0.0")

    private fun numberShape(shape: Shape, typeName: String, defaultValue: String = "0"): Symbol {
        return createSymbolBuilder(shape, typeName, namespace = "kotlin")
            .defaultValue(defaultValue)
            .build()
    }

    override fun bigIntegerShape(shape: BigIntegerShape?): Symbol = createBigSymbol(shape, "BigInteger")

    override fun bigDecimalShape(shape: BigDecimalShape?): Symbol = createBigSymbol(shape, "BigDecimal")

    private fun createBigSymbol(shape: Shape?, symbolName: String): Symbol {
        return createSymbolBuilder(shape, symbolName, namespace = "java.math", boxed = true).build()
    }

    override fun stringShape(shape: StringShape): Symbol {
        val enumTrait = shape.getTrait(EnumTrait::class.java)
        if (enumTrait.isPresent) {
            return createEnumSymbol(shape, enumTrait.get())
        }
        return createSymbolBuilder(shape, "String", boxed = true, namespace = "kotlin").build()
    }

    fun createEnumSymbol(shape: StringShape, trait: EnumTrait): Symbol {
        val namespace = "$rootNamespace.model"
        return createSymbolBuilder(shape, shape.defaultName(), namespace, boxed = true)
            .definitionFile("${shape.defaultName()}.kt")
            .build()
    }

    override fun booleanShape(shape: BooleanShape?): Symbol =
        createSymbolBuilder(shape, "Boolean", namespace = "kotlin").defaultValue("false").build()

    override fun structureShape(shape: StructureShape): Symbol {
        val name = shape.defaultName()
        val namespace = "$rootNamespace.model"
        val builder = createSymbolBuilder(shape, name, namespace, boxed = true)
            .definitionFile("${shape.defaultName()}.kt")

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
            val ref = SymbolReference.builder()
                .symbol(memberSymbol)
                .options(SymbolReference.ContextOption.DECLARE)
                .build()
            builder.addReference(ref)

            val targetShape = model.expectShape(it.target)
            if (targetShape is CollectionShape) {
                val targetSymbol = toSymbol(targetShape)
                targetSymbol.references.forEach { builder.addReference(it) }
            }
        }
    }

    override fun listShape(shape: ListShape): Symbol {
        val reference = toSymbol(shape.member)
        val valueType = if (shape.hasTrait(SparseTrait::class.java)) "${reference.name}?" else reference.name

        return createSymbolBuilder(shape, "List<$valueType>", boxed = true)
            .addReference(reference)
            .putProperty(MUTABLE_COLLECTION_FUNCTION, "mutableListOf<$valueType>")
            .putProperty(IMMUTABLE_COLLECTION_FUNCTION, "listOf<$valueType>")
            .build()
    }

    override fun mapShape(shape: MapShape): Symbol {
        val reference = toSymbol(shape.value)
        val valueType = if (shape.hasTrait(SparseTrait::class.java)) "${reference.name}?" else reference.name

        return createSymbolBuilder(shape, "Map<String, $valueType>", boxed = true)
            .addReference(reference)
            .putProperty(MUTABLE_COLLECTION_FUNCTION, "mutableMapOf<String, $valueType>")
            .putProperty(IMMUTABLE_COLLECTION_FUNCTION, "mapOf<String, $valueType>")
            .build()
    }

    override fun setShape(shape: SetShape): Symbol {
        val reference = toSymbol(shape.member)
        return createSymbolBuilder(shape, "Set<${reference.name}>", boxed = true)
            .addReference(reference)
            .putProperty(MUTABLE_COLLECTION_FUNCTION, "mutableSetOf<${reference.name}>")
            .putProperty(IMMUTABLE_COLLECTION_FUNCTION, "setOf<${reference.name}>")
            .build()
    }

    override fun memberShape(shape: MemberShape): Symbol {
        val targetShape =
            model.getShape(shape.target).orElseThrow { CodegenException("Shape not found: ${shape.target}") }
        return toSymbol(targetShape)
    }

    override fun timestampShape(shape: TimestampShape?): Symbol {
        val dependency = KotlinDependency.CLIENT_RT_CORE
        return createSymbolBuilder(shape, "Instant", boxed = true)
            .namespace("${dependency.namespace}.time", ".")
            .addDependency(dependency)
            .build()
    }

    override fun blobShape(shape: BlobShape): Symbol {
        return if (shape.hasTrait(StreamingTrait::class.java)) {
            val dependency = KotlinDependency.CLIENT_RT_CORE
            createSymbolBuilder(shape, "ByteStream", boxed = true)
                .namespace("${dependency.namespace}.content", ".")
                .addDependency(dependency)
                .build()
        } else {
            createSymbolBuilder(shape, "ByteArray", boxed = true, namespace = "kotlin").build()
        }
    }

    override fun documentShape(shape: DocumentShape?): Symbol {
        val dependency = KotlinDependency.CLIENT_RT_CORE
        return createSymbolBuilder(shape, "Document", boxed = true)
            .namespace("${dependency.namespace}.smithy", ".")
            .addDependency(dependency)
            .build()
    }

    override fun unionShape(shape: UnionShape): Symbol {
        val name = shape.defaultName()
        val namespace = "$rootNamespace.model"
        val builder = createSymbolBuilder(shape, name, namespace, boxed = true)
            .definitionFile("${shape.id.name}.kt")

        // add a reference to each member symbol
        addDeclareMemberReferences(builder, shape.allMembers.values)

        return builder.build()
    }

    override fun resourceShape(shape: ResourceShape?): Symbol {
        return createSymbolBuilder(shape, "Resource").build()
    }

    override fun operationShape(shape: OperationShape?): Symbol {
        // The Kotlin SDK does not produce code explicitly based on Operations
        error { "Unexpected codegen code path" }
    }

    override fun serviceShape(shape: ServiceShape): Symbol {
        val serviceName = sdkId.clientName()
        return createSymbolBuilder(shape, "${serviceName}Client")
            .namespace(rootNamespace, ".")
            .definitionFile("${serviceName}Client.kt").build()
    }

    /**
     * Creates a symbol builder for the shape with the given type name in the root namespace.
     */
    private fun createSymbolBuilder(shape: Shape?, typeName: String, boxed: Boolean = false): Symbol.Builder {
        val builder = Symbol.builder()
            .putProperty(SHAPE_KEY, shape)
            .name(typeName)

        val explicitlyBoxed = shape?.hasTrait(BoxTrait::class.java) ?: false
        if (explicitlyBoxed || boxed) {
            builder.boxed()
        }
        return builder
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
        boxed: Boolean = false
    ): Symbol.Builder {
        return createSymbolBuilder(shape, typeName, boxed)
            .namespace(namespace, ".")
    }
}

// See https://awslabs.github.io/smithy/1.0/spec/aws/aws-core.html#using-sdk-service-id-for-client-naming
fun String.clientName(): String =
    split(" ").map { it.toLowerCase().capitalize() }.joinToString(separator = "") { it }

/**
 * Mark a symbol as being boxed (nullable) i.e. `T?`
 */
fun Symbol.Builder.boxed(): Symbol.Builder = apply { putProperty(BOXED_KEY, true) }

/**
 * Set the default value used when formatting the symbol
 */
fun Symbol.Builder.defaultValue(value: String): Symbol.Builder = apply { putProperty(DEFAULT_VALUE_KEY, value) }

/**
 * Convenience function for specifying kotlin namespace
 */
fun Symbol.Builder.namespace(name: String): Symbol.Builder = namespace(name, ".")

/**
 * Convenience function for specifying a symbol is a subnamespace of the [dependency].
 *
 * This will implicitly add the dependecy to the builder since it is known that it comes from the
 * dependency namespace.
 *
 * Equivalent to:
 * ```
 * builder.addDependency(dependency)
 * builder.namespace("${dependency.namespace}.subnamespace")
 * ```
 */
fun Symbol.Builder.namespace(dependency: KotlinDependency, subnamespace: String = ""): Symbol.Builder {
    addDependency(dependency)
    return if (subnamespace.isEmpty()) {
        namespace(dependency.namespace)
    } else {
        namespace("${dependency.namespace}.${subnamespace.trimStart('.')}")
    }
}

/**
 * Add a reference to a symbol coming from a kotlin dependency
 */
fun Symbol.Builder.addReference(dependency: KotlinDependency, name: String, subnamespace: String = ""): Symbol.Builder {
    val refSymbol = Symbol.builder()
        .name(name)
        .namespace(dependency, subnamespace)
        .build()
    return addReference(refSymbol)
}

/**
 * Add a reference to the given symbol with the context option
 */
fun Symbol.Builder.addReference(symbol: Symbol, option: SymbolReference.ContextOption): Symbol.Builder {
    val ref = SymbolReference.builder()
        .symbol(symbol)
        .options(option)
        .build()

    return addReference(ref)
}
