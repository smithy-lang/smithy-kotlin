/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.BoxTrait
import software.amazon.smithy.utils.StringUtils
import java.util.logging.Logger


// PropertyBag keys

// The key that holds the default value for a type (symbol) as a string
private const val DEFAULT_VALUE_KEY: String = "defaultValue"

// Boolean property indicating this symbol should be boxed
private const val BOXED_KEY: String = "boxed"

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
 */
fun Symbol.defaultValue(): String? {
    // boxed types should always be defaulted to null
    if (isBoxed()) {
        return "null"
    }

    val default = getProperty(DEFAULT_VALUE_KEY, String::class.java)
    return if (default.isPresent) default.get() else null
}

fun Shape.defaultName(): String = StringUtils.capitalize(this.id.name)

fun MemberShape.defaultName(): String = StringUtils.uncapitalize(this.memberName)


class SymbolVisitor(private val model: Model) : SymbolProvider, ShapeVisitor<Symbol> {
    val LOGGER = Logger.getLogger(javaClass.name)
    private val escaper: ReservedWordSymbolProvider.Escaper

    init {
        // FIXME - Loading of reserved-words.txt file from resources fails randomly with exception:
        //  `Projection source failed: java.io.UncheckedIOException: java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)`
        // For now we are going to hard code the hard reserved words and debug this later
//        val reservedWords = ReservedWordsBuilder()
//            .loadWords(KotlinCodegenPlugin::class.java.getResource("reserved-words.txt"))
//            .build()

        val hardReservedWords = listOf(
            "as",
            "as?",
            "break",
            "class",
            "continue",
            "do",
            "else",
            "false",
            "for",
            "fun",
            "if",
            "in",
            "!in",
            "interface",
            "is",
            "!is",
            "null",
            "object",
            "package",
            "return",
            "super",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "when",
            "while"
        )
        val reservedWords = ReservedWordsBuilder().apply {
            hardReservedWords.forEach { put(it, "_$it") }
        }.build()

        escaper = ReservedWordSymbolProvider.builder()
            .nameReservedWords(reservedWords)
            // only escape words when the symbol has a definition file to prevent escaping intentional references to built-in shapes
            .escapePredicate() { _, symbol -> !symbol.definitionFile.isEmpty() }
            .buildEscaper()
    }

    override fun toSymbol(shape: Shape): Symbol {
        val symbol: Symbol = shape.accept(this)
        LOGGER.info("creating symbol from $shape: $symbol")
        return escaper.escapeSymbol(shape, symbol)
    }

    override fun toMemberName(shape: MemberShape): String {
        return escaper.escapeMemberName(shape.defaultName())
    }

    override fun byteShape(shape: ByteShape?): Symbol = numberShape(shape, "Byte")

    override fun integerShape(shape: IntegerShape?): Symbol = numberShape(shape, "Integer")

    override fun shortShape(shape: ShortShape?): Symbol = numberShape(shape, "Short")

    override fun longShape(shape: LongShape?): Symbol = numberShape(shape, "Long")

    override fun floatShape(shape: FloatShape?): Symbol = numberShape(shape, "Float", "0.0f")

    override fun doubleShape(shape: DoubleShape?): Symbol = numberShape(shape, "Double", "0.0")

    private fun numberShape(shape: Shape?, typeName: String, defaultValue: String = "0"): Symbol {
        return createSymbolBuilder(shape, typeName).putProperty(DEFAULT_VALUE_KEY, defaultValue).build()
    }

    override fun bigIntegerShape(shape: BigIntegerShape?): Symbol = createBigSymbol(shape, "BigInteger")

    override fun bigDecimalShape(shape: BigDecimalShape?): Symbol = createBigSymbol(shape, "BigDecimal")

    private fun createBigSymbol(shape: Shape?, symbolName: String): Symbol {
        return createSymbolBuilder(shape, symbolName, boxed = true)
            .addReference(createNamespaceReference(KotlinDependency.BIG, symbolName))
            .build()
    }

    // TODO - handle enum types
    override fun stringShape(shape: StringShape?): Symbol = createSymbolBuilder(shape, "String", boxed = true).build()

    override fun booleanShape(shape: BooleanShape?): Symbol = createSymbolBuilder(shape, "Boolean").putProperty(DEFAULT_VALUE_KEY, "false").build()

    override fun structureShape(shape: StructureShape): Symbol {
        val name = shape.defaultName()
        // TODO - handle error types
        return createSymbolBuilder(shape, name, boxed = true).definitionFile("./models/${shape.id.name}.kt").build()
    }

    override fun listShape(shape: ListShape): Symbol {
        val reference = toSymbol(shape.member)
        return createSymbolBuilder(shape, "List<${reference.name}>", boxed = true).addReference(reference).build()
    }

    override fun mapShape(shape: MapShape): Symbol {
        val reference = toSymbol(shape.value)
        return createSymbolBuilder(shape, "Map<String, ${reference.name}>", boxed = true).addReference(reference).build()
    }

    override fun setShape(shape: SetShape): Symbol {
        val reference = toSymbol(shape.member)
        return createSymbolBuilder(shape, "Set<${reference.name}>", boxed = true).addReference(reference).build()
    }

    override fun memberShape(shape: MemberShape): Symbol {
        val targetShape =
            model.getShape(shape.target).orElseThrow { CodegenException("Shape not found: ${shape.target}") }
        return toSymbol(targetShape)
    }

    override fun timestampShape(shape: TimestampShape?): Symbol {
        return createSymbolBuilder(shape, "TimestampTODO", boxed = true).build()
    }

    override fun blobShape(shape: BlobShape?): Symbol {
        return createSymbolBuilder(shape, "ByteArray", boxed = true).build()
    }

    override fun documentShape(shape: DocumentShape?): Symbol {
        return createSymbolBuilder(shape, "DocumentTODO", boxed = true).build()
    }

    override fun unionShape(shape: UnionShape?): Symbol {
        return createSymbolBuilder(shape, "UnionTODO").build()
    }

    override fun resourceShape(shape: ResourceShape?): Symbol {
        return createSymbolBuilder(shape, "Resource").build()
    }

    override fun operationShape(shape: OperationShape?): Symbol {
        return createSymbolBuilder(shape, "OperationTODO").build()
    }

    override fun serviceShape(shape: ServiceShape?): Symbol {
        return createSymbolBuilder(shape, "Client").definitionFile("./Client.kt").build()
    }

    private fun createSymbolBuilder(shape: Shape?, typeName: String, boxed: Boolean = false): Symbol.Builder {
        val builder = Symbol.builder().putProperty("shape", shape).name(typeName)
        val explicitlyBoxed = shape?.hasTrait(BoxTrait::class.java) ?: false
        if (explicitlyBoxed || boxed) {
            builder.putProperty(BOXED_KEY, true)
        }
        return builder
    }

    private fun createSymbolBuilder(
        shape: Shape?,
        typeName: String,
        namespace: String,
        boxed: Boolean = false
    ): Symbol.Builder {
        return createSymbolBuilder(shape, typeName, boxed).namespace(namespace, ".")
    }

    fun createNamespaceReference(dependency: KotlinDependency, alias: String): SymbolReference {
        val namespace = dependency.namespace
        val nsSymbol = Symbol.builder()
            .name(alias)
            .namespace(namespace, ".")
            .build()
        return SymbolReference.builder().symbol(nsSymbol).alias(alias).build()
    }
}