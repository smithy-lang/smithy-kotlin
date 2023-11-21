/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.DefaultValueSerializationMode
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.TimestampFormatTrait
import kotlin.test.Test

class HttpStringValuesMapSerializerTest {
    private val defaultModel = loadModelFromResource("http-binding-protocol-generator-test.smithy")

    private fun getTestContents(model: Model, operationId: String, location: HttpBinding.Location, settings: KotlinSettings? = null): String {
        val resolvedSettings = settings ?: model.defaultSettings(TestModelDefault.SERVICE_NAME, TestModelDefault.NAMESPACE)
        val testCtx = model.newTestContext(settings = resolvedSettings)
        val httpGenerator = testCtx.generator as HttpBindingProtocolGenerator
        val resolver = httpGenerator.getProtocolHttpBindingResolver(testCtx.generationCtx.model, testCtx.generationCtx.service)
        val op = model.expectShape<OperationShape>(operationId)
        val bindings = resolver.requestBindings(op).filter {
            it.location == location
        }

        val writer = KotlinWriter("test")
        HttpStringValuesMapSerializer(testCtx.generationCtx, bindings, resolver, TimestampFormatTrait.Format.EPOCH_SECONDS).render(writer)
        return writer.toString()
    }

    @Test
    fun `it handles primitive header shapes always mode`() {
        val settings = defaultModel.defaultSettings(defaultValueSerializationMode = DefaultValueSerializationMode.ALWAYS)
        val contents = getTestContents(defaultModel, "com.test#PrimitiveShapesOperation", HttpBinding.Location.HEADER, settings)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            append("X-d", "${'$'}{input.hBool}")
            append("X-c", "${'$'}{input.hFloat}")
            append("X-a", "${'$'}{input.hInt}")
            append("X-b", "${'$'}{input.hLong}")
            append("X-required", "${'$'}{input.hRequiredInt}")
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles primitive header shapes when different mode`() {
        val contents = getTestContents(defaultModel, "com.test#PrimitiveShapesOperation", HttpBinding.Location.HEADER)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            if (input.hBool != false) append("X-d", "${'$'}{input.hBool}")
            if (input.hFloat != 0f) append("X-c", "${'$'}{input.hFloat}")
            if (input.hInt != 0) append("X-a", "${'$'}{input.hInt}")
            if (input.hLong != 0L) append("X-b", "${'$'}{input.hLong}")
            append("X-required", "${'$'}{input.hRequiredInt}")
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles primitive query shapes when different mode`() {
        val contents = getTestContents(defaultModel, "com.test#PrimitiveShapesOperation", HttpBinding.Location.QUERY)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            if (input.qInt != 0) labels.add("q-int", "${'$'}{input.qInt}")
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles primitive query shapes always mode`() {
        val settings = defaultModel.defaultSettings(defaultValueSerializationMode = DefaultValueSerializationMode.ALWAYS)
        val contents = getTestContents(defaultModel, "com.test#PrimitiveShapesOperation", HttpBinding.Location.QUERY, settings)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            labels.add("q-int", "${'$'}{input.qInt}")
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles enum shapes`() {
        val contents = getTestContents(defaultModel, "com.test#EnumInput", HttpBinding.Location.HEADER)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            if (input.enumHeader != null) append("X-EnumHeader", input.enumHeader.value)
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles enum default value when different mode`() {
        val model = """
            @http(method: "POST", uri: "/foo")
            operation Foo {
                input: FooRequest
            }
            
            enum MyEnum {
                Variant1,
                Variant2
            }
            
            intEnum MyIntEnum {
                Tay = 1
                Lep = 2
            }
            
            structure FooRequest {
                @default("Variant1")
                @httpHeader("X-EnumHeader")
                enumHeader: MyEnum
                
                @default(2)
                @httpHeader("X-IntEnumHeader")
                intEnumHeader: MyIntEnum
            }
        """.prependNamespaceAndService(operations = listOf("Foo")).toSmithyModel()

        val contents = getTestContents(model, "com.test#Foo", HttpBinding.Location.HEADER)
        contents.assertBalancedBracesAndParens()

        val intEnumValue = "\${input.intEnumHeader.value}"
        val expectedContents = """
            if (input.enumHeader != com.test.model.MyEnum.fromValue("Variant1")) append("X-EnumHeader", input.enumHeader.value)
            if (input.intEnumHeader != com.test.model.MyIntEnum.fromValue(2)) append("X-IntEnumHeader", "$intEnumValue")
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles string shapes`() {
        val contents = getTestContents(defaultModel, "com.test#SmokeTest", HttpBinding.Location.HEADER)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            if (input.header1?.isNotEmpty() == true) append("X-Header1", input.header1)
            if (input.header2?.isNotEmpty() == true) append("X-Header2", input.header2)
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles blob and media type trait`() {
        val contents = getTestContents(defaultModel, "com.test#BlobInput", HttpBinding.Location.HEADER)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            if (input.headerMediaType?.isNotEmpty() == true) append("X-Blob", input.headerMediaType.encodeBase64())
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles collections`() {
        val contents = getTestContents(defaultModel, "com.test#HeaderListInput", HttpBinding.Location.HEADER)
        contents.assertBalancedBracesAndParens()

        val expectedContents = """
            if (input.enumList?.isNotEmpty() == true) appendAll("x-enumList", input.enumList.map { quoteHeaderValue(it.value) })
            if (input.intList?.isNotEmpty() == true) appendAll("x-intList", input.intList.map { "${'$'}it" })
            if (input.strList?.isNotEmpty() == true) appendAll("x-strList", input.strList.map { quoteHeaderValue(it) })
            if (input.tsList?.isNotEmpty() == true) appendAll("x-tsList", input.tsList.map { it.format(TimestampFormat.RFC_5322) })
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it handles timestamps`() {
        val headerContents = getTestContents(defaultModel, "com.test#TimestampInput", HttpBinding.Location.HEADER)
        headerContents.assertBalancedBracesAndParens()
        val expectedHeaderContents = """
            if (input.headerDateTime != null) append("X-DateTime", input.headerDateTime.format(TimestampFormat.ISO_8601))
            if (input.headerEpoch != null) append("X-Epoch", input.headerEpoch.format(TimestampFormat.EPOCH_SECONDS))
            if (input.headerHttpDate != null) append("X-Date", input.headerHttpDate.format(TimestampFormat.RFC_5322))
        """.trimIndent()
        headerContents.shouldContainOnlyOnceWithDiff(expectedHeaderContents)

        val queryContents = getTestContents(defaultModel, "com.test#TimestampInput", HttpBinding.Location.QUERY)
        val expectedQueryContents = """
            if (input.queryTimestamp != null) labels.add("qtime", input.queryTimestamp.format(TimestampFormat.ISO_8601))
            if (input.queryTimestampList?.isNotEmpty() == true) labels.addAll("qtimeList", input.queryTimestampList.map { it.format(TimestampFormat.ISO_8601) })
        """.trimIndent()
        queryContents.shouldContainOnlyOnceWithDiff(expectedQueryContents)
    }
}
