/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.integration

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.kotlin.codegen.expectShape
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

class HttpStringValuesMapSerializerTest {
    private val defaultModel = HttpBindingProtocolGeneratorTest::class.java.getResource("http-binding-protocol-generator-test.smithy").asSmithy()

    private fun getTestContents(model: Model, operationId: String, location: HttpBinding.Location): String {
        val testCtx = model.newTestContext()
        val httpGenerator = testCtx.generator as HttpBindingProtocolGenerator
        val resolver = httpGenerator.getProtocolHttpBindingResolver(testCtx.generationCtx)
        val op = model.expectShape<OperationShape>(operationId)
        val bindings = resolver.requestBindings(op).filter {
            it.location == location
        }

        val writer = KotlinWriter("test")
        HttpStringValuesMapSerializer(testCtx.generationCtx, bindings, resolver, TimestampFormatTrait.Format.EPOCH_SECONDS).render(writer)
        return writer.toString()
    }

    @Test
    fun `it handles primitive header shapes`() {
        val contents = getTestContents(defaultModel, "com.test#PrimitiveShapesOperation", HttpBinding.Location.HEADER)
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
            if (input.hBool != false) append("X-d", "${'$'}{input.hBool}")
            if (input.hFloat != 0.0f) append("X-c", "${'$'}{input.hFloat}")
            if (input.hInt != 0) append("X-a", "${'$'}{input.hInt}")
            if (input.hLong != 0L) append("X-b", "${'$'}{input.hLong}")
            append("X-required", "${'$'}{input.hRequiredInt}")
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles primitive query shapes`() {
        val contents = getTestContents(defaultModel, "com.test#PrimitiveShapesOperation", HttpBinding.Location.QUERY)
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
            if (input.qInt != 0) append("q-int", "${'$'}{input.qInt}")
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles enum shapes`() {
        val contents = getTestContents(defaultModel, "com.test#EnumInput", HttpBinding.Location.HEADER)
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
            if (input.enumHeader != null) append("X-EnumHeader", input.enumHeader.value)
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles string shapes`() {
        val contents = getTestContents(defaultModel, "com.test#SmokeTest", HttpBinding.Location.HEADER)
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
            if (input.header1?.isNotEmpty() == true) append("X-Header1", input.header1)
            if (input.header2?.isNotEmpty() == true) append("X-Header2", input.header2)
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles blob and media type trait`() {
        val contents = getTestContents(defaultModel, "com.test#BlobInput", HttpBinding.Location.HEADER)
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
            if (input.headerMediaType?.isNotEmpty() == true) append("X-Blob", input.headerMediaType.encodeBase64())
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles collections`() {
        val contents = getTestContents(defaultModel, "com.test#HeaderListInput", HttpBinding.Location.HEADER)
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
            if (input.enumList?.isNotEmpty() == true) appendAll("x-enumList", input.enumList.map { it.value })
            if (input.intList?.isNotEmpty() == true) appendAll("x-intList", input.intList.map { "${'$'}it" })
            if (input.tsList?.isNotEmpty() == true) appendAll("x-tsList", input.tsList.map { it.format(TimestampFormat.RFC_5322) })
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles timestamps`() {
        val headerContents = getTestContents(defaultModel, "com.test#TimestampInput", HttpBinding.Location.HEADER)
        headerContents.shouldSyntacticSanityCheck()
        val expectedHeaderContents = """
            if (input.headerDateTime != null) append("X-DateTime", input.headerDateTime.format(TimestampFormat.ISO_8601))
            if (input.headerEpoch != null) append("X-Epoch", input.headerEpoch.format(TimestampFormat.EPOCH_SECONDS))
            if (input.headerHttpDate != null) append("X-Date", input.headerHttpDate.format(TimestampFormat.RFC_5322))
        """.trimIndent()
        headerContents.shouldContainOnlyOnceWithDiff(expectedHeaderContents)

        val queryContents = getTestContents(defaultModel, "com.test#TimestampInput", HttpBinding.Location.QUERY)
        val expectedQueryContents = """
            if (input.queryTimestamp != null) append("qtime", input.queryTimestamp.format(TimestampFormat.ISO_8601))
            if (input.queryTimestampList?.isNotEmpty() == true) appendAll("qtimeList", input.queryTimestampList.map { it.format(TimestampFormat.ISO_8601) })
        """.trimIndent()
        queryContents.shouldContainOnlyOnceWithDiff(expectedQueryContents)
    }
}
