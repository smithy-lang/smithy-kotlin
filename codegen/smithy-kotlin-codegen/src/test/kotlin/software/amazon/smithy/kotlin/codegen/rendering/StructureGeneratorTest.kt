/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import org.junit.jupiter.api.TestInstance
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RenderingContext
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.model.traits.SYNTHETIC_NAMESPACE
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.kotlin.codegen.trimEveryLine
import software.amazon.smithy.model.shapes.StructureShape
import kotlin.test.Test
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructureGeneratorTest {
    // Structure generation is rather involved, instead of one giant substr search to see that everything is right we
    // look for parts of the whole as individual tests
    private val commonTestContents: String
    private val deprecatedTestContents: String

    init {
        commonTestContents = generateStructure(
            """
                structure Qux { }

                @documentation("This *is* documentation about the shape.")
                structure MyStruct {
                    foo: String,
                    object: String,
                    @documentation("This *is* documentation about the member.")
                    bar: PrimitiveInteger,
                    baz: Integer,
                    Quux: Qux,
                    byteValue: Byte
                }
            """,
        )

        deprecatedTestContents = generateStructure(
            """
                structure Qux { }

                @deprecated
                structure MyStruct {
                    foo: String,
                    bar: String,

                    @deprecated
                    baz: Qux,
                }
            """,
        )
    }

    @Test
    fun `it renders package decl`() {
        assertTrue(commonTestContents.contains("package com.test"))
    }

    @Test
    fun `it syntactic sanity checks`() {
        // sanity check since we are testing fragments
        commonTestContents.assertBalancedBracesAndParens()
    }

    @Test
    fun `it renders constructors`() {
        val expectedClassDecl = """
            public class MyStruct private constructor(builder: Builder) {
                /**
                 * This *is* documentation about the member.
                 */
                public val bar: kotlin.Int = builder.bar
                public val baz: kotlin.Int? = builder.baz
                public val byteValue: kotlin.Byte? = builder.byteValue
                public val foo: kotlin.String? = builder.foo
                public val `object`: kotlin.String? = builder.`object`
                public val quux: com.test.model.Qux? = builder.quux
        """.formatForTest(indent = "")

        commonTestContents.shouldContainOnlyOnceWithDiff(expectedClassDecl)
    }

    @Test
    fun `it renders a companion object`() {
        val expected = """
            public companion object {
                public operator fun invoke(block: Builder.() -> kotlin.Unit): com.test.model.MyStruct = Builder().apply(block).build()
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a toString implementation`() {
        val expected = """
            override fun toString(): kotlin.String = buildString {
                append("MyStruct(")
                append("bar=${'$'}bar,")
                append("baz=${'$'}baz,")
                append("byteValue=${'$'}byteValue,")
                append("foo=${'$'}foo,")
                append("object=${'$'}`object`,")
                append("quux=${'$'}quux")
                append(")")
            }
        """.formatForTest()

        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a hashCode implementation`() {
        val expected = """
        override fun hashCode(): kotlin.Int {
            var result = bar
            result = 31 * result + (baz ?: 0)
            result = 31 * result + (byteValue?.toInt() ?: 0)
            result = 31 * result + (foo?.hashCode() ?: 0)
            result = 31 * result + (`object`?.hashCode() ?: 0)
            result = 31 * result + (quux?.hashCode() ?: 0)
            return result
        }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders an equals implementation`() {
        val expected = """
            override fun equals(other: kotlin.Any?): kotlin.Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
        
                other as MyStruct
        
                if (bar != other.bar) return false
                if (baz != other.baz) return false
                if (byteValue != other.byteValue) return false
                if (foo != other.foo) return false
                if (`object` != other.`object`) return false
                if (quux != other.quux) return false
        
                return true
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a copy implementation`() {
        val expected = """
            public inline fun copy(block: Builder.() -> kotlin.Unit = {}): com.test.model.MyStruct = Builder(this).apply(block).build()
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a builder impl`() {
        val expected = """
            public class Builder {
                /**
                 * This *is* documentation about the member.
                 */
                public var bar: kotlin.Int = 0
                public var baz: kotlin.Int? = null
                public var byteValue: kotlin.Byte? = null
                public var foo: kotlin.String? = null
                public var `object`: kotlin.String? = null
                public var quux: com.test.model.Qux? = null
        
                @PublishedApi
                internal constructor()
                @PublishedApi
                internal constructor(x: com.test.model.MyStruct) : this() {
                    this.bar = x.bar
                    this.baz = x.baz
                    this.byteValue = x.byteValue
                    this.foo = x.foo
                    this.`object` = x.`object`
                    this.quux = x.quux
                }
        
                @PublishedApi
                internal fun build(): com.test.model.MyStruct = MyStruct(this)
                
                /**
                 * construct an [com.test.model.Qux] inside the given [block]
                 */
                public fun quux(block: com.test.model.Qux.Builder.() -> kotlin.Unit) {
                    this.quux = com.test.model.Qux.invoke(block)
                }
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders class docs`() {
        commonTestContents.shouldContainOnlyOnceWithDiff("This *is* documentation about the shape.")
    }

    @Test
    fun `it renders member docs`() {
        commonTestContents.shouldContainWithDiff("This *is* documentation about the member.")
    }

    @Test
    fun `it handles shape and member docs`() {
        val model = """
            structure Foo {
                @documentation("Member documentation")
                baz: Baz,

                bar: Baz,

                qux: String
            }

            @documentation("Shape documentation")
            string Baz
        """.prependNamespaceAndService().toSmithyModel()

        /*
            The effective documentation trait of a shape is resolved using the following process:
            1. Use the documentation trait of the shape, if present.
            2. If the shape is a member, then use the documentation trait of the shape targeted by the member, if present.

            the effective documentation of Foo$baz resolves to "Member documentation", Foo$bar resolves to "Shape documentation",
            Foo$qux is not documented, Baz resolves to "Shape documentation", and Foo is not documented.
        */

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#Foo")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        generated.shouldContainWithDiff("Shape documentation")
        generated.shouldContainWithDiff("Member documentation")
    }

    @Test
    fun `it handles the sensitive trait in toString`() {
        val model = """           
            @sensitive
            string Baz
            
            structure Foo {
                bar: Baz,
                @documentation("Member documentation")
                baz: Baz,
                qux: String
            }
            
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#Foo")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        val expected = """
            override fun toString(): kotlin.String = buildString {
                append("Foo(")
                append("bar=*** Sensitive Data Redacted ***,")
                append("baz=*** Sensitive Data Redacted ***,")
                append("qux=${'$'}qux")
                append(")")
            }
        """.formatForTest()
        generated.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testSensitiveTraitOnParent() {
        val model = """           
            string Baz
            
            @sensitive
            structure Foo {
                bar: Baz,
                @documentation("Member documentation")
                baz: Baz,
                qux: String
            }
            
            structure Parent {
                foo: Foo
            }
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#Foo")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        val expected = """
            override fun toString(): kotlin.String = buildString {
                append("Foo(")
                append("*** Sensitive Data Redacted ***")
                append(")")
            }
        """.formatForTest()
        generated.shouldContainOnlyOnceWithDiff(expected)

        val writer2 = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct2 = model.expectShape<StructureShape>("com.test#Parent")
        val renderingCtx2 = RenderingContext(writer2, struct2, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx2).render()
        val generated2 = writer2.toString()
        val expected2 = """
            override fun toString(): kotlin.String = buildString {
                append("Parent(")
                append("foo=*** Sensitive Data Redacted ***")
                append(")")
            }
        """.formatForTest()
        generated2.shouldContainOnlyOnceWithDiff(expected2)
    }

    @Test
    fun `it handles required HTTP fields in initializers`() {
        val model = """
            @http(method: "POST", uri: "/foo/{bar}/{baz}")
            operation Foo {
                input: FooRequest
            }
            
            structure FooRequest {
                @required
                @httpLabel
                bar: String,
                
                @httpLabel
                @required
                baz: Integer,
                
                @httpPayload
                qux: String,
                
                @required
                @httpQuery("quux")
                quux: Boolean,
                
                @httpQuery("corge")
                corge: String,
                
                @required
                @length(min: 0)
                @httpQuery("grault")
                grault: String,
                
                @required
                @length(min: 3)
                @httpQuery("garply")
                garply: String
            }
        """.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#FooRequest")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        val expected = """
            public class FooRequest private constructor(builder: Builder) {
                public val bar: kotlin.String? = requireNotNull(builder.bar) { "A non-null value must be provided for bar" }
                public val baz: kotlin.Int = requireNotNull(builder.baz) { "A non-null value must be provided for baz" }
                public val corge: kotlin.String? = builder.corge
                public val garply: kotlin.String? = requireNotNull(builder.garply) { "A non-null value must be provided for garply" }
                    .apply { require(isNotBlank()) { "A non-blank value must be provided for garply" } }
                public val grault: kotlin.String? = requireNotNull(builder.grault) { "A non-null value must be provided for grault" }
                public val quux: kotlin.Boolean = requireNotNull(builder.quux) { "A non-null value must be provided for quux" }
                public val qux: kotlin.String? = builder.qux
        """.formatForTest(indent = "")
        generated.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it handles required query params in initializers`() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest
            }
            
            map MapOfStrings {
                key: String,
                value: String
            }
            
            structure FooRequest {
                @required
                @httpQueryParams
                bar: MapOfStrings
                
                @httpPayload
                baz: String
            }
        """.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#FooRequest")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        val expected = """
            public class FooRequest private constructor(builder: Builder) {
                public val bar: Map<String, String>? = requireNotNull(builder.bar) { "A non-null value must be provided for bar" }
                public val baz: kotlin.String? = builder.baz
        """.formatForTest(indent = "")
        generated.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it handles blob shapes`() {
        // blobs (with and without streaming) require special attention in equals() and hashCode() implementations
        val model = """
            @streaming
            blob BlobStream
            
            structure MyStruct {
                foo: Blob,
                bar: BlobStream
            }
            
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#MyStruct")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        val expectedEqualsContent = """
            override fun equals(other: kotlin.Any?): kotlin.Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false
        
                other as MyStruct
        
                if (bar != other.bar) return false
                if (foo != null) {
                    if (other.foo == null) return false
                    if (!foo.contentEquals(other.foo)) return false
                } else if (other.foo != null) return false
        
                return true
            }
        """.formatForTest()

        val expectedHashCodeContent = """
            override fun hashCode(): kotlin.Int {
                var result = bar?.hashCode() ?: 0
                result = 31 * result + (foo?.contentHashCode() ?: 0)
                return result
            }
        """.formatForTest()
        contents.shouldContainOnlyOnceWithDiff(expectedEqualsContent)
        contents.shouldContainOnlyOnceWithDiff(expectedHashCodeContent)
    }

    @Test
    fun `it generates collection types for maps with enum values`() {
        val model = """
            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                input: GetFooInput
            }
            
            @enum([
                {
                    value: "rawValue1",
                    name: "Variant1"
                },
                {
                    value: "rawValue2",
                    name: "Variant2"
                }
            ])
            string MyEnum
            
            map EnumMap {
                key: String,
                value: MyEnum
            }
            
            structure GetFooInput {
                enumMap: EnumMap
            }
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.REST_JSON, operations = listOf("GetFoo"), serviceName = "Example")
            .toSmithyModel()
        val struct = model.expectShape<StructureShape>("com.test#GetFooInput")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, serviceName = "Example")
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings(serviceName = "Example"))
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        listOf(
            "public val enumMap: Map<String, MyEnum>? = builder.enumMap",
            "public var enumMap: Map<String, MyEnum>? = null",
        ).forEach { line ->
            contents.shouldContainOnlyOnceWithDiff(line)
        }
    }

    @Test
    fun `it generates collection types for sparse maps with enum values`() {
        val model = """
            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                input: GetFooInput
            }
            
            @enum([
                {
                    value: "rawValue1",
                    name: "Variant1"
                },
                {
                    value: "rawValue2",
                    name: "Variant2"
                }
            ])
            string MyEnum
            
            @sparse
            map EnumMap {
                key: String,
                value: MyEnum
            }
            
            structure GetFooInput {
                enumMap: EnumMap
            }
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.REST_JSON, operations = listOf("GetFoo"), serviceName = "Example")
            .toSmithyModel()
        val struct = model.expectShape<StructureShape>("com.test#GetFooInput")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, serviceName = "Example")
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings(serviceName = "Example"))
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        listOf(
            "public val enumMap: Map<String, MyEnum?>? = builder.enumMap",
            "public var enumMap: Map<String, MyEnum?>? = null",
        ).forEach { line ->
            contents.shouldContainOnlyOnceWithDiff(line)
        }
    }

    @Test
    fun `it annotates deprecated structures`() {
        deprecatedTestContents.shouldContainOnlyOnceWithDiff(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public class MyStruct private constructor(builder: Builder) {
            """.trimIndent(),
        )
    }

    @Test
    fun `it annotates deprecated members`() {
        deprecatedTestContents.trimEveryLine().shouldContainOnlyOnceWithDiff(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public val baz: com.test.model.Qux? = builder.baz
            """.trimIndent(),
        )
    }

    @Test
    fun `it annotates deprecated builder members`() {
        deprecatedTestContents.trimEveryLine().shouldContainOnlyOnceWithDiff(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public val baz: com.test.model.Qux? = builder.baz
            """.trimIndent(),
        )
    }

    @Test
    fun `it renders client errors`() {
        testErrorShape(
            "client",
            null,
            null,
            null,
            "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Client",
        )
    }

    @Test
    fun `it renders server errors`() {
        testErrorShape(
            "server",
            null,
            null,
            null,
            "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Server",
        )
    }

    @Test
    fun `it renders retryable errors`() {
        testErrorShape(
            "client",
            "@retryable",
            "sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true",
            "sdkErrorMetadata.attributes[ErrorMetadata.ThrottlingError] = false",
            "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Client",
        )
    }

    @Test
    fun `it renders retryable throttling errors`() {
        testErrorShape(
            "client",
            "@retryable(throttling: true)",
            "sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true",
            "sdkErrorMetadata.attributes[ErrorMetadata.ThrottlingError] = true",
            "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Client",
        )
    }

    private fun testErrorShape(
        errorType: String,
        retryableTrait: String?,
        initRetryableLine: String?,
        initThrottlingLine: String?,
        initErrorTypeLine: String?,
    ) {
        val model = listOfNotNull(
            "@error(\"$errorType\")",
            retryableTrait,
            "structure FooError { }",
        )
            .joinToString("\n")
            .prependNamespaceAndService()
            .toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#FooError")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        val expectedInit = listOfNotNull(
            "init {",
            initRetryableLine.indent(),
            initThrottlingLine.indent(),
            initErrorTypeLine.indent(),
            "}",
        )
            .joinToString("\n")
            .formatForTest()

        contents.shouldContainOnlyOnceWithDiff(expectedInit)
    }

    private fun generateStructure(model: String): String {
        val fullModel = model.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(fullModel)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = fullModel.expectShape<StructureShape>("com.test#MyStruct")
        val renderingCtx = RenderingContext(writer, struct, fullModel, provider, fullModel.defaultSettings())
        val generator = StructureGenerator(renderingCtx)
        generator.render()

        return writer.toString()
    }

    @Test
    fun testEventStreams() {
        // event streams require special attention

        val model = """
            operation GetFoos {
                output: GetFoosResponse
            }
            
            @streaming
            union Events {
                a: Event1,
                b: Event2,
                c: Event3,
            }
            
            structure Event1{}
            structure Event2{}
            structure Event3{}
            
            structure GetFoosResponse {
                events: Events
            }
            
        """.prependNamespaceAndService(operations = listOf("GetFoos")).toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)

        // we have to use the synthetic operation input/output to actually trigger all the logic
        val struct = model.expectShape<StructureShape>("$SYNTHETIC_NAMESPACE.test#GetFoosResponse")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        val expectedProperty = """
            public val events: Flow<com.test.model.Events>? = builder.events
        """.formatForTest()
        contents.shouldContainOnlyOnceWithDiff(expectedProperty)

        val expectedEqualsContent = """
            override fun equals(other: kotlin.Any?): kotlin.Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as GetFoosResponse

                if (events != other.events) return false

                return true
            }
        """.formatForTest()

        val expectedHashCodeContent = """
            override fun hashCode(): kotlin.Int {
                var result = events?.hashCode() ?: 0
                return result
            }
        """.formatForTest()
        contents.shouldContainOnlyOnceWithDiff(expectedEqualsContent)
        contents.shouldContainOnlyOnceWithDiff(expectedHashCodeContent)
    }

    @Test
    fun testNullableReferences() {
        val model = """
            structure Struct {
                aInt: Integer
                @default(0)
                bInt: Integer
            }
        """
            .trimIndent()
            .prependNamespaceAndService(version = "2.0")
            .toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#Struct")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        val expected = """
            override fun hashCode(): kotlin.Int {
                var result = aInt ?: 0
                result = 31 * result + (bInt)
                return result
            }
        """.formatForTest()
        generated.shouldContainOnlyOnceWithDiff(expected)
    }
}

private fun String?.indent(indentation: String = "    "): String? = if (this == null) null else "$indentation$this"
