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

class ShapeDeserializerGeneratorTest {
    private fun renderDeserializerFor(model: String, shapeId: String): String {
        val smithyModel = model.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()
        val ctx = smithyModel.newTestContext()
        val writer = ctx.newWriter()
        val shape = smithyModel.expectShape(ShapeId.from(shapeId))
        ShapeDeserializerGenerator(smithyModel, ctx.generationCtx.symbolProvider, shape).render(writer)
        return writer.toString()
    }

    @Test
    fun readsEnumMemberThroughFromValue() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird {
                kind: Kind
                rank: Rank
            }

            enum Kind {
                SPARROW = "sparrow"
            }

            intEnum Rank {
                FIRST = 1
            }
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        contents.shouldContainOnlyOnceWithDiff("\"kind\" -> builder.kind = com.test.model.Kind.fromValue(d.readString(member))")
        contents.shouldContainOnlyOnceWithDiff("\"rank\" -> builder.rank = com.test.model.Rank.fromValue(d.readInt(member))")
    }

    @Test
    fun readsEnumListElementThroughFromValue() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird { kinds: KindList }

            list KindList { member: Kind }

            enum Kind {
                SPARROW = "sparrow"
            }
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        contents.shouldContainOnlyOnceWithDiff("val v = com.test.model.Kind.fromValue(e.readString(KINDS_ELEMENT))")
    }

    @Test
    fun readsEnumMapValueThroughFromValue() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird { kinds: KindMap }

            map KindMap { key: String, value: Kind }

            enum Kind {
                SPARROW = "sparrow"
            }
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        contents.shouldContainOnlyOnceWithDiff("val v = com.test.model.Kind.fromValue(e.readString(KINDS_VALUE))")
    }

    @Test
    fun readsEnumMapKeyIntoEnumKeyedAccumulator() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird { counts: KindCounts }

            map KindCounts { key: Kind, value: Integer }

            enum Kind {
                SPARROW = "sparrow"
            }
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        contents.shouldContainOnlyOnceWithDiff("val out = mutableMapOf<com.test.model.Kind, kotlin.Int>()")
        contents.shouldContainOnlyOnceWithDiff("map[com.test.model.Kind.fromValue(k)] = v")
    }

    @Test
    fun takesAccumulatorSlotNullabilityFromMember() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird {
                data: DocumentMap
                attrs: StringMap
                colors: ColorList
            }

            map DocumentMap { key: String, value: Document }

            map StringMap { key: String, value: String }

            @sparse
            list ColorList { member: String }
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        // a document value may legally be null even though the map is not @sparse
        contents.shouldContainOnlyOnceWithDiff("val out = mutableMapOf<kotlin.String, aws.smithy.kotlin.runtime.content.Document?>()")
        contents.shouldContainOnlyOnceWithDiff("val out = mutableMapOf<kotlin.String, kotlin.String>()")
        contents.shouldContainOnlyOnceWithDiff("val out = mutableListOf<kotlin.String?>()")
        contents.shouldContainOnlyOnceWithDiff("list.add(null)")
    }

    @Test
    fun omitsStreamingMemberFromDeserializeWalk() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird {
                name: String

                @required
                song: SongStream
            }

            @streaming
            blob SongStream
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        contents.shouldContainOnlyOnceWithDiff("\"name\" -> builder.name = d.readString(member)")
        contents.shouldNotContainWithDiff("\"song\" ->")
    }

    @Test
    fun readsNestedListsWithDistinctNamesAtEachDepth() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird { grid: ColorGrid }

            list ColorGrid { member: ColorList }

            list ColorList { member: String }
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        contents.shouldContainOnlyOnceWithDiff("d.readList(GRID, out) { list, e ->")
        contents.shouldContainOnlyOnceWithDiff("e.readList(GRID_ELEMENT, out1) { list1, e1 ->")
        contents.shouldContainOnlyOnceWithDiff("val v1 = e1.readString(GRID_ELEMENT_ELEMENT)")
        contents.shouldContainOnlyOnceWithDiff("list1.add(v1)")
        contents.shouldContainOnlyOnceWithDiff("list.add(v)")
    }

    @Test
    fun readsNestedMapsWithDistinctNamesAtEachDepth() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Bird }

            structure Bird { regions: RegionMap }

            map RegionMap { key: String, value: StringMap }

            map StringMap { key: String, value: String }
        """.trimIndent()

        val contents = renderDeserializerFor(model, "com.test#Bird")

        contents.shouldContainOnlyOnceWithDiff("d.readMap(REGIONS, out) { map, k, e ->")
        contents.shouldContainOnlyOnceWithDiff("e.readMap(REGIONS_VALUE, out1) { map1, k1, e1 ->")
        contents.shouldContainOnlyOnceWithDiff("val v1 = e1.readString(REGIONS_VALUE_VALUE)")
        contents.shouldContainOnlyOnceWithDiff("map1[k1] = v1")
        contents.shouldContainOnlyOnceWithDiff("map[k] = v")
    }
}
