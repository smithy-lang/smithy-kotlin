/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.model

import aws.smithy.kotlin.codegen.model.traits.OperationInput
import aws.smithy.kotlin.codegen.model.traits.OperationOutput
import aws.smithy.kotlin.codegen.model.traits.SyntheticClone
import aws.smithy.kotlin.codegen.test.toSmithyModel
import io.kotest.matchers.string.shouldContain
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import kotlin.test.*
import kotlin.test.Test

class OperationNormalizerTest {

    @Test
    fun `it adds inputs and outputs to empty operations`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Empty]
            }
            operation Empty {}
        """.toSmithyModel(applyDefaultTransforms = false)
        val origOp = model.expectShape<OperationShape>("com.test#Empty")
        assertFalse(origOp.input.isPresent)
        assertFalse(origOp.output.isPresent)
        val normalized = OperationNormalizer.transform(model, ShapeId.from("com.test#Example"))

        val op = normalized.expectShape<OperationShape>("com.test#Empty")
        assertTrue(op.input.isPresent)
        assertTrue(op.output.isPresent)

        val input = normalized.expectShape<StructureShape>(op.input.get())
        val output = normalized.expectShape<StructureShape>(op.output.get())
        input.expectTrait<SyntheticClone>()
        input.expectTrait<OperationInput>()
        output.expectTrait<SyntheticClone>()
        output.expectTrait<OperationOutput>()
    }

    @Test
    fun `it clones operation inputs`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Foo]
            }
            
            operation Foo {
                input: MyInput
            }
            
            structure MyInput {
                v: String
            }
        """.toSmithyModel(applyDefaultTransforms = false)
        val origId = ShapeId.from("com.test#MyInput")
        val normalized = OperationNormalizer.transform(model, ShapeId.from("com.test#Example"))

        val op = normalized.expectShape<OperationShape>("com.test#Foo")

        val input = normalized.expectShape<StructureShape>(op.input.get())
        normalized.expectShape<StructureShape>(op.output.get())

        // the normalization process leaves the cloned shape in the model
        assertTrue(normalized.getShape(ShapeId.from("com.test#MyInput")).isPresent)

        val syntheticTrait = input.expectTrait<SyntheticClone>()
        assertEquals(origId, syntheticTrait.archetype)
        val expected = ShapeId.from("smithy.kotlin.synthetic.test#FooRequest")
        assertEquals(expected, input.id)
    }

    @Test
    fun `it clones operation outputs`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Foo]
            }
            
            operation Foo {
                output: MyOutput
            }
            
            structure MyOutput {
                v: String
            }
        """.toSmithyModel(applyDefaultTransforms = false)
        val origId = ShapeId.from("com.test#MyOutput")
        val normalized = OperationNormalizer.transform(model, ShapeId.from("com.test#Example"))

        val op = normalized.expectShape<OperationShape>("com.test#Foo")

        normalized.expectShape<StructureShape>(op.input.get())
        val output = normalized.expectShape<StructureShape>(op.output.get())

        val syntheticTrait = output.expectTrait<SyntheticClone>()
        assertEquals(origId, syntheticTrait.archetype)
        val expected = ShapeId.from("smithy.kotlin.synthetic.test#FooResponse")
        assertEquals(expected, output.id)
    }

    @Test
    fun `it does not modify non operational shapes`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Foo]
            }
            
            operation Foo {
                output: MyOutput
            }
            
            structure MyOutput {
                v: String,
                nested: Nested
            }
            structure Nested {
                foo: String
            }
        """.toSmithyModel(applyDefaultTransforms = false)
        val normalized = OperationNormalizer.transform(model, ShapeId.from("com.test#Example"))
        val expected = ShapeId.from("com.test#Nested")
        normalized.expectShape<StructureShape>(expected)

        val op = normalized.expectShape<OperationShape>("com.test#Foo")
        val output = normalized.expectShape<StructureShape>(op.output.get())
        assertEquals(expected, output.getMember("nested").get().target)
    }

    @Test
    fun `it fails on conflicting rename`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Foo]
            }
            
            operation Foo {
                output: MyOutput
            }
            
            structure MyOutput {
                foo: FooResponse,
            }
            
            structure FooResponse {
                foo: String
            }
        """.toSmithyModel(applyDefaultTransforms = false)

        val ex = assertFailsWith(CodegenException::class) {
            OperationNormalizer.transform(model, ShapeId.from("com.test#Example"))
        }
        ex.message!!.shouldContain("com.test#FooResponse")
    }

    @Test
    fun `it fails on conflicting rename of a shape with traits`() {
        // any applied trait (e.g. documentation) must not exempt a shape from conflict checking
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Foo]
            }

            operation Foo {
                output: MyOutput
            }

            structure MyOutput {
                foo: FooResponse,
            }

            @documentation("a documented shape")
            structure FooResponse {
                foo: String
            }
        """.toSmithyModel(applyDefaultTransforms = false)

        val ex = assertFailsWith(CodegenException::class) {
            OperationNormalizer.transform(model, ShapeId.from("com.test#Example"))
        }
        ex.message!!.shouldContain("renaming operation inputs or outputs will result in a conflict for:")
        ex.message!!.shouldContain("com.test#FooResponse")
    }

    private val exampleService = ShapeId.from("com.test#Example")

    private fun Model.closureIds(): Set<ShapeId> = Walker(this).walkShapes(expectShape(exampleService)).map { it.id }.toSet()

    private fun Model.memberTarget(memberId: String): ShapeId = expectShape<MemberShape>(memberId).target

    @Test
    fun `it redirects member references to a same-name operation output`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [GetFoo, GetSummary]
            }

            operation GetFoo {
                output: GetFooResponse
            }

            operation GetSummary {
                output: GetSummaryResponse
            }

            structure GetFooResponse {
                v: String
            }

            structure GetSummaryResponse {
                foo: GetFooResponse
            }
        """.toSmithyModel(applyDefaultTransforms = false)
        val normalized = OperationNormalizer.transform(model, exampleService)

        val syntheticFoo = ShapeId.from("smithy.kotlin.synthetic.test#GetFooResponse")
        assertEquals(syntheticFoo, normalized.memberTarget("smithy.kotlin.synthetic.test#GetSummaryResponse\$foo"))
        assertEquals(syntheticFoo, normalized.expectShape<StructureShape>("smithy.kotlin.synthetic.test#GetSummaryResponse").getMember("foo").get().target)

        val closure = normalized.closureIds()
        assertFalse(ShapeId.from("com.test#GetFooResponse") in closure)
        assertFalse(ShapeId.from("com.test#GetSummaryResponse") in closure)

        // the originals are left in the model untouched
        assertEquals(ShapeId.from("com.test#GetFooResponse"), normalized.memberTarget("com.test#GetSummaryResponse\$foo"))
    }

    @Test
    fun `it redirects collection and map references to a same-name operation input`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [PutFoo, PutFoos]
            }

            operation PutFoo {
                input: PutFooRequest
            }

            operation PutFoos {
                input: PutFoosRequest
            }

            structure PutFooRequest {
                v: String
            }

            structure PutFoosRequest {
                list: FooList,
                map: FooMap
            }

            list FooList {
                member: PutFooRequest
            }

            map FooMap {
                key: String,
                value: PutFooRequest
            }
        """.toSmithyModel(applyDefaultTransforms = false)
        val normalized = OperationNormalizer.transform(model, exampleService)

        val syntheticPutFoo = ShapeId.from("smithy.kotlin.synthetic.test#PutFooRequest")
        assertEquals(syntheticPutFoo, normalized.memberTarget("com.test#FooList\$member"))
        assertEquals(syntheticPutFoo, normalized.memberTarget("com.test#FooMap\$value"))
        assertEquals(syntheticPutFoo, normalized.expectShape<ListShape>("com.test#FooList").member.target)
        assertFalse(ShapeId.from("com.test#PutFooRequest") in normalized.closureIds())
    }

    @Test
    fun `it redirects recursive references to a same-name operation output`() {
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [GetNode]
            }

            operation GetNode {
                output: GetNodeResponse
            }

            structure GetNodeResponse {
                next: GetNodeResponse
            }
        """.toSmithyModel(applyDefaultTransforms = false)
        val normalized = OperationNormalizer.transform(model, exampleService)

        val syntheticNode = ShapeId.from("smithy.kotlin.synthetic.test#GetNodeResponse")
        assertEquals(syntheticNode, normalized.memberTarget("smithy.kotlin.synthetic.test#GetNodeResponse\$next"))
        assertFalse(ShapeId.from("com.test#GetNodeResponse") in normalized.closureIds())
    }

    @Test
    fun `it does not redirect references to differently named operation outputs`() {
        // e.g. lambda's FunctionConfiguration is the output of several operations and also a member of
        // GetFunctionResponse. It must keep generating its own FunctionConfiguration type.
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [GetFunction, GetFunctionConfiguration, UpdateFunctionConfiguration]
            }

            operation GetFunction {
                output: GetFunctionResponse
            }

            operation GetFunctionConfiguration {
                output: FunctionConfiguration
            }

            operation UpdateFunctionConfiguration {
                output: FunctionConfiguration
            }

            structure GetFunctionResponse {
                configuration: FunctionConfiguration
            }

            structure FunctionConfiguration {
                name: String
            }
        """.toSmithyModel(applyDefaultTransforms = false)
        val normalized = OperationNormalizer.transform(model, exampleService)

        val original = ShapeId.from("com.test#FunctionConfiguration")
        assertEquals(original, normalized.memberTarget("smithy.kotlin.synthetic.test#GetFunctionResponse\$configuration"))
        assertTrue(original in normalized.closureIds())
    }

    @Test
    fun `it fails when a remaining reference conflicts with a synthetic clone`() {
        // Bar's output is named like Foo's synthetic input. It isn't Foo's input so it can't be redirected, and
        // since Other still references it, both would generate a FooRequest type.
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Foo, Bar, GetOther]
            }

            operation Foo {
                input: FooInput
            }

            operation Bar {
                output: FooRequest
            }

            operation GetOther {
                output: Other
            }

            structure FooInput {
                v: String
            }

            structure FooRequest {
                v: String
            }

            structure Other {
                foo: FooRequest
            }
        """.toSmithyModel(applyDefaultTransforms = false)

        val ex = assertFailsWith(CodegenException::class) {
            OperationNormalizer.transform(model, exampleService)
        }
        ex.message!!.shouldContain("com.test#FooRequest (conflicts with smithy.kotlin.synthetic.test#FooRequest; referenced by: smithy.kotlin.synthetic.test#GetOtherResponse)")
    }

    @Test
    fun `it fails when a remaining reference to a shape with traits conflicts with a synthetic clone`() {
        // Same as above, but any applied trait (e.g. documentation) must not exempt a shape from conflict checking.
        // FooRequest is Bar's output, so validateTransform exempts it and this exercises the post-redirect check.
        val model = """
            namespace com.test
            service Example {
                version: "1.0.0",
                operations: [Foo, Bar, GetOther]
            }

            operation Foo {
                input: FooInput
            }

            operation Bar {
                output: FooRequest
            }

            operation GetOther {
                output: Other
            }

            structure FooInput {
                v: String
            }

            @documentation("a documented shape")
            structure FooRequest {
                v: String
            }

            structure Other {
                foo: FooRequest
            }
        """.toSmithyModel(applyDefaultTransforms = false)

        val ex = assertFailsWith(CodegenException::class) {
            OperationNormalizer.transform(model, exampleService)
        }
        ex.message!!.shouldContain("normalizing operation inputs or outputs left shapes in the service closure")
        ex.message!!.shouldContain("com.test#FooRequest (conflicts with smithy.kotlin.synthetic.test#FooRequest; referenced by: smithy.kotlin.synthetic.test#GetOtherResponse)")
    }
}
