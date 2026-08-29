/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.newWriter
import aws.smithy.kotlin.codegen.test.prependNamespaceAndService
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.shouldNotContainWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test

class SchemaGeneratorTest {
    private fun renderSchemaFor(
        model: String,
        shapeId: String,
        operations: List<String> = listOf("Foo"),
        imports: List<String> = emptyList(),
    ): String {
        val smithyModel = model.prependNamespaceAndService(imports = imports, operations = operations).toSmithyModel()
        val ctx = smithyModel.newTestContext()
        val writer = ctx.newWriter()
        val shape = smithyModel.expectShape(ShapeId.from(shapeId))
        SchemaGenerator(smithyModel, ctx.generationCtx.symbolProvider, shape).render(writer)
        return writer.toString()
    }

    @Test
    fun itRendersStructureSchemaOnCompanion() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird {
                @jsonName("bird_name")
                name: String
                colors: ColorList
                nest: Nest
            }

            structure Nest { height: Integer }

            list ColorList { member: String }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#Bird")

        // SCHEMA on the companion, with the shape id
        contents.shouldContainOnlyOnceWithDiff("public val SCHEMA: StructureSchema = StructureSchema(shapeId(\"com.test#Bird\")) {")
        // simple member via prelude + trait
        contents.shouldContainOnlyOnceWithDiff("member(\"name\", PreludeSchemas.String, JsonNameTrait(\"bird_name\"))")
        // list member inlined
        contents.shouldContainOnlyOnceWithDiff("member(\"colors\", ListSchema(shapeId(\"com.test#ColorList\")) {")
        contents.shouldContainOnlyOnceWithDiff("element(PreludeSchemas.String)")
        // structure member referenced via its own SCHEMA
        contents.shouldContainOnlyOnceWithDiff("member(\"nest\", Nest.SCHEMA)")
        // extracted member vals
        contents.shouldContainOnlyOnceWithDiff("public val NAME: MemberSchema = SCHEMA.member(\"name\")!!")
        contents.shouldContainOnlyOnceWithDiff("public val COLORS: MemberSchema = SCHEMA.member(\"colors\")!!")
        contents.shouldContainOnlyOnceWithDiff("public val COLORS_ELEMENT: MemberSchema = (COLORS.target as ListSchema).element")
        contents.shouldContainOnlyOnceWithDiff("public val NEST: MemberSchema = SCHEMA.member(\"nest\")!!")
    }

    @Test
    fun itRendersMapMemberAndAccessors() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Holder }

            structure Holder { attrs: AttributeMap }

            @sparse
            map AttributeMap { key: String, value: String }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#Holder")
        contents.shouldContainOnlyOnceWithDiff("member(\"attrs\", MapSchema(shapeId(\"com.test#AttributeMap\")) {")
        contents.shouldContainOnlyOnceWithDiff("trait(SparseTrait)")
        contents.shouldContainOnlyOnceWithDiff("key(PreludeSchemas.String)")
        contents.shouldContainOnlyOnceWithDiff("value(PreludeSchemas.String)")
        contents.shouldContainOnlyOnceWithDiff("public val ATTRS_KEY: MemberSchema = (ATTRS.target as MapSchema).key")
        contents.shouldContainOnlyOnceWithDiff("public val ATTRS_VALUE: MemberSchema = (ATTRS.target as MapSchema).value")
    }

    @Test
    fun itWiresRecursiveMemberLazily() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: RecursiveValue }

            structure RecursiveValue {
                child: RecursiveValue
            }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#RecursiveValue")
        contents.shouldContainOnlyOnceWithDiff("member(\"child\", lazy { RecursiveValue.SCHEMA })")
    }

    @Test
    fun itWiresMutuallyRecursiveTargetsLazily() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: A }

            structure A { b: B }

            structure B { a: A }
        """.trimIndent()

        renderSchemaFor(model, "com.test#A")
            .shouldContainOnlyOnceWithDiff("member(\"b\", lazy { B.SCHEMA })")
        renderSchemaFor(model, "com.test#B")
            .shouldContainOnlyOnceWithDiff("member(\"a\", lazy { A.SCHEMA })")
    }

    @Test
    fun itWiresCycleThroughCollectionLazily() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Tree }

            structure Tree { branches: BranchList }

            list BranchList { member: Branch }

            structure Branch { tree: Tree }
        """.trimIndent()

        renderSchemaFor(model, "com.test#Tree")
            .shouldContainOnlyOnceWithDiff("element(lazy { Branch.SCHEMA })")
        renderSchemaFor(model, "com.test#Branch")
            .shouldContainOnlyOnceWithDiff("member(\"tree\", lazy { Tree.SCHEMA })")
    }

    @Test
    fun itWiresNonRecursiveTargetsEagerly() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Top }

            structure Top { nest: Nest }

            structure Nest { leaf: Leaf }

            structure Leaf { name: String }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#Top")
        contents.shouldContainOnlyOnceWithDiff("member(\"nest\", Nest.SCHEMA)")
        contents.shouldNotContainWithDiff("lazy {")
    }

    @Test
    fun itDeconflictsAMemberNamedSchema() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Holder }

            structure Holder { schema: String }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#Holder")
        contents.shouldContainOnlyOnceWithDiff("public val SCHEMA: StructureSchema = StructureSchema(shapeId(\"com.test#Holder\")) {")
        contents.shouldContainOnlyOnceWithDiff("public val SCHEMA_2: MemberSchema = SCHEMA.member(\"schema\")!!")
        // the member's constant is suffixed rather than emitting a second `public val SCHEMA`
        contents.shouldContainOnlyOnceWithDiff("public val SCHEMA:")
    }

    @Test
    fun itDeconflictsAMemberNamedAfterItsTarget() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Holder }

            structure Holder { b: B }

            structure B { name: String }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#Holder")
        contents.shouldContainOnlyOnceWithDiff("member(\"b\", B.SCHEMA)")
        contents.shouldContainOnlyOnceWithDiff("public val B_2: MemberSchema = SCHEMA.member(\"b\")!!")
    }

    @Test
    fun itDeconflictsAMemberNamedAfterASynthesizedElementSlot() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Holder }

            structure Holder {
                foo: StringList
                fooElement: String
            }

            list StringList { member: String }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#Holder")
        contents.shouldContainOnlyOnceWithDiff("public val FOO: MemberSchema = SCHEMA.member(\"foo\")!!")
        contents.shouldContainOnlyOnceWithDiff("public val FOO_ELEMENT: MemberSchema = (FOO.target as ListSchema).element")
        contents.shouldContainOnlyOnceWithDiff("public val FOO_ELEMENT_2: MemberSchema = SCHEMA.member(\"fooElement\")!!")
    }

    @Test
    fun itDropsEventStreamErrorVariants() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Holder }

            structure Holder {
                @httpPayload
                events: Events
            }

            @streaming
            union Events {
                msg: Msg
                err: BadRequest
            }

            structure Msg { data: String }

            @error("client")
            structure BadRequest { message: String }
        """.trimIndent()

        val contents = renderSchemaFor(model, "com.test#Events")
        contents.shouldContainOnlyOnceWithDiff("member(\"msg\", Msg.SCHEMA)")
        contents.shouldNotContainWithDiff("member(\"err\"")
        contents.shouldNotContainWithDiff("public val ERR")
    }
}
