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

class ShapeSerializerGeneratorTest {
    private fun renderSerializeFor(model: String, shapeId: String): String {
        val smithyModel = model.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()
        val ctx = smithyModel.newTestContext()
        val writer = ctx.newWriter()
        val shape = smithyModel.expectShape(ShapeId.from(shapeId))
        ShapeSerializerGenerator(smithyModel, ctx.generationCtx.symbolProvider, shape).render(writer)
        return writer.toString()
    }

    private fun renderSchemaFor(model: String, shapeId: String): String {
        val smithyModel = model.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()
        val ctx = smithyModel.newTestContext()
        val writer = ctx.newWriter()
        val shape = smithyModel.expectShape(ShapeId.from(shapeId))
        SchemaGenerator(smithyModel, ctx.generationCtx.symbolProvider, shape).render(writer)
        return writer.toString()
    }

    // enums, intEnums and every kind of slot that can hold one
    private val enumModel = """
        @http(method: "POST", uri: "/foo")
        operation Foo { input: Holder }

        structure Holder {
            status: Status
            level: Level
            statuses: StatusList
            byStatus: StatusValueMap
            keyed: StatusKeyMap
            choice: Choice
        }

        enum Status {
            ON = "on"
            OFF = "off"
        }

        intEnum Level {
            LOW = 1
            HIGH = 2
        }

        list StatusList { member: Status }

        map StatusValueMap { key: String, value: Status }

        map StatusKeyMap { key: Status, value: String }

        union Choice { status: Status, level: Level }
    """.trimIndent()

    private val errorModel = """
        @http(method: "POST", uri: "/foo")
        operation Foo { input: Holder, errors: [NotFound] }

        structure Holder { name: String }

        @error("client")
        structure NotFound {
            @required
            message: String

            resourceId: String
        }
    """.trimIndent()

    @Test
    fun itWritesSimpleMembersWithTheWriteFunctionOfTheirType() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Simple }

            structure Simple {
                aBool: Boolean
                aByte: Byte
                aShort: Short
                anInt: Integer
                aLong: Long
                aFloat: Float
                aDouble: Double
                aBigInt: BigInteger
                aBigDec: BigDecimal
                aString: String
                aBlob: Blob
                aTimestamp: Timestamp
                aDoc: Document
            }
        """.trimIndent()

        val contents = renderSerializeFor(model, "com.test#Simple")

        contents.shouldContainOnlyOnceWithDiff("override fun serialize(serializer: ShapeSerializer<*>): kotlin.Unit = serializer.writeStruct(SCHEMA) { serializeMembers(this) }")
        contents.shouldContainOnlyOnceWithDiff("override fun serializeMembers(dest: StructSerializer): kotlin.Unit = with(dest) {")
        contents.shouldContainOnlyOnceWithDiff("writeBoolean(A_BOOL, it)")
        contents.shouldContainOnlyOnceWithDiff("writeByte(A_BYTE, it)")
        contents.shouldContainOnlyOnceWithDiff("writeShort(A_SHORT, it)")
        contents.shouldContainOnlyOnceWithDiff("writeInt(AN_INT, it)")
        contents.shouldContainOnlyOnceWithDiff("writeLong(A_LONG, it)")
        contents.shouldContainOnlyOnceWithDiff("writeFloat(A_FLOAT, it)")
        contents.shouldContainOnlyOnceWithDiff("writeDouble(A_DOUBLE, it)")
        contents.shouldContainOnlyOnceWithDiff("writeBigInteger(A_BIG_INT, it)")
        contents.shouldContainOnlyOnceWithDiff("writeBigDecimal(A_BIG_DEC, it)")
        contents.shouldContainOnlyOnceWithDiff("writeString(A_STRING, it)")
        contents.shouldContainOnlyOnceWithDiff("writeBlob(A_BLOB, it)")
        contents.shouldContainOnlyOnceWithDiff("writeTimestamp(A_TIMESTAMP, it)")
        contents.shouldContainOnlyOnceWithDiff("writeDocument(A_DOC, it)")
    }

    @Test
    fun itWritesTheRawValueOfEnumAndIntEnumMembers() {
        val contents = renderSerializeFor(enumModel, "com.test#Holder")

        contents.shouldContainOnlyOnceWithDiff("writeString(STATUS, it.value)")
        contents.shouldContainOnlyOnceWithDiff("writeInt(LEVEL, it.value)")
    }

    @Test
    fun itWritesTheRawValueOfEnumsInCollectionSlots() {
        val contents = renderSerializeFor(enumModel, "com.test#Holder")

        // list element
        contents.shouldContainOnlyOnceWithDiff("writeString(STATUSES_ELEMENT, elem.value)")
        // map value
        contents.shouldContainOnlyOnceWithDiff("writeString(BY_STATUS_VALUE, v.value)")
        // map key: the key expression narrows to the raw value, the wire key stays a String
        contents.shouldContainOnlyOnceWithDiff("entry(KEYED_KEY, k.value) {")
        contents.shouldContainOnlyOnceWithDiff("writeString(KEYED_VALUE, v)")
    }

    @Test
    fun itWritesTheRawValueOfEnumUnionVariants() {
        val contents = renderSerializeFor(enumModel, "com.test#Choice")

        contents.shouldContainOnlyOnceWithDiff("is Choice.Status -> writeString(STATUS, this@Choice.value.value)")
        contents.shouldContainOnlyOnceWithDiff("is Choice.Level -> writeInt(LEVEL, this@Choice.value.value)")
    }

    @Test
    fun itReadsUnionVariantsOffTheSmartCastReceiver() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Holder }

            structure Holder { choice: Choice }

            union Choice {
                name: String
                nest: Nest
                items: StringList
            }

            structure Nest { height: Integer }

            list StringList { member: String }
        """.trimIndent()

        val contents = renderSerializeFor(model, "com.test#Choice")

        contents.shouldContainOnlyOnceWithDiff("when (this@Choice) {")
        contents.shouldContainOnlyOnceWithDiff("is Choice.Name -> writeString(NAME, this@Choice.value)")
        contents.shouldContainOnlyOnceWithDiff("is Choice.Nest -> writeStruct(NEST, this@Choice.value)")
        contents.shouldContainOnlyOnceWithDiff("is Choice.Items -> writeList(ITEMS, this@Choice.value.size) {")
        contents.shouldContainOnlyOnceWithDiff("this@Choice.value.forEach { elem ->")
        contents.shouldContainOnlyOnceWithDiff("is Choice.SdkUnknown -> {}")
        // a redundant cast of the smart-cast receiver is an error under -Werror
        contents.shouldNotContainWithDiff("as Choice")
    }

    @Test
    fun itOmitsStreamingBlobPayloadsFromTheWalkButKeepsTheirMemberSchema() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: StreamingRequest }

            structure StreamingRequest {
                @required
                @httpPayload
                body: StreamingBlob

                @httpHeader("x-name")
                name: String
            }

            @streaming
            blob StreamingBlob
        """.trimIndent()

        val serialize = renderSerializeFor(model, "com.test#StreamingRequest")
        serialize.shouldContainOnlyOnceWithDiff("writeString(NAME, it)")
        serialize.shouldNotContainWithDiff("BODY")

        val schema = renderSchemaFor(model, "com.test#StreamingRequest")
        schema.shouldContainOnlyOnceWithDiff("member(\"body\", SimpleSchema(shapeId(\"com.test#StreamingBlob\"), ShapeType.BLOB, StreamingTrait), RequiredTrait, HttpPayloadTrait)")
        schema.shouldContainOnlyOnceWithDiff("public val BODY: MemberSchema = SCHEMA.member(\"body\")!!")
    }

    @Test
    fun itOmitsEventStreamMembersFromTheWalkButKeepsTheirMemberSchema() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: EventRequest }

            structure EventRequest {
                events: Events
                name: String
            }

            @streaming
            union Events { message: MessageEvent }

            structure MessageEvent { text: String }
        """.trimIndent()

        val serialize = renderSerializeFor(model, "com.test#EventRequest")
        serialize.shouldContainOnlyOnceWithDiff("writeString(NAME, it)")
        serialize.shouldNotContainWithDiff("EVENTS")

        val schema = renderSchemaFor(model, "com.test#EventRequest")
        schema.shouldContainOnlyOnceWithDiff("member(\"events\", Events.SCHEMA)")
        schema.shouldContainOnlyOnceWithDiff("public val EVENTS: MemberSchema = SCHEMA.member(\"events\")!!")
    }

    @Test
    fun itDoesNotShadowTheLambdaParametersOfNestedCollections() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo { input: Holder }

            structure Holder {
                matrix: StringListList
                nested: StringMapMap
            }

            list StringListList { member: StringList }

            list StringList { member: String }

            map StringMapMap { key: String, value: StringMap }

            map StringMap { key: String, value: String }
        """.trimIndent()

        val contents = renderSerializeFor(model, "com.test#Holder")

        // list of lists: the inner element is `elem2`, and the inner list is iterated off the outer `elem`
        contents.shouldContainOnlyOnceWithDiff("it.forEach { elem ->")
        contents.shouldContainOnlyOnceWithDiff("writeList(MATRIX_ELEMENT, elem.size) {")
        contents.shouldContainOnlyOnceWithDiff("elem.forEach { elem2 ->")
        contents.shouldContainOnlyOnceWithDiff("writeString(MATRIX_ELEMENT_ELEMENT, elem2)")

        // map of maps: the inner entry is destructured to `(k2, v2)`
        contents.shouldContainOnlyOnceWithDiff("it.forEach { (k, v) ->")
        contents.shouldContainOnlyOnceWithDiff("writeMap(NESTED_VALUE, v.size) {")
        contents.shouldContainOnlyOnceWithDiff("v.forEach { (k2, v2) ->")
        contents.shouldContainOnlyOnceWithDiff("entry(NESTED_VALUE_KEY, k2) {")
        contents.shouldContainOnlyOnceWithDiff("writeString(NESTED_VALUE_VALUE, v2)")
    }

    @Test
    fun itDoesNotCaptureAModeledMessageForANonErrorShape() {
        val contents = renderSerializeFor(errorModel, "com.test#Holder")

        contents.shouldContainOnlyOnceWithDiff("this@Holder.name?.let {")
        contents.shouldNotContainWithDiff("modeledMessage")
    }
}
