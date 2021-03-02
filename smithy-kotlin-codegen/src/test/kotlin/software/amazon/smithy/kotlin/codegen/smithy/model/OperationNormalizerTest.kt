/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.smithy.model

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.asSmithyModel
import software.amazon.smithy.kotlin.codegen.expectTrait
import software.amazon.smithy.kotlin.codegen.smithy.expectShape
import software.amazon.smithy.kotlin.codegen.smithy.traits.SyntheticClone
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.StructureShape
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationNormalizerTest {

    @Test
    fun `it adds inputs and outputs to empty operations`() {
        val model = """
            namespace smithy.test
            operation Empty {}
        """.asSmithyModel()
        val origOp = model.expectShape<OperationShape>("smithy.test#Empty")
        assertFalse(origOp.input.isPresent)
        assertFalse(origOp.output.isPresent)
        val normalized = OperationNormalizer().transform(model)

        val op = normalized.expectShape<OperationShape>("smithy.test#Empty")
        assertTrue(op.input.isPresent)
        assertTrue(op.output.isPresent)

        val input = normalized.expectShape<StructureShape>(op.input.get())
        val output = normalized.expectShape<StructureShape>(op.output.get())
        input.expectTrait<SyntheticClone>()
        output.expectTrait<SyntheticClone>()
    }

    @Test
    fun `it clones operation inputs`() {
        val model = """
            namespace smithy.test
            operation Foo {
                input: MyInput
            }
            
            structure MyInput {
                v: String
            }
        """.asSmithyModel()
        val origId = ShapeId.from("smithy.test#MyInput")
        val normalized = OperationNormalizer().transform(model)

        val op = normalized.expectShape<OperationShape>("smithy.test#Foo")

        val input = normalized.expectShape<StructureShape>(op.input.get())
        normalized.expectShape<StructureShape>(op.output.get())

        val syntheticTrait = input.expectTrait<SyntheticClone>()
        assertEquals(origId, syntheticTrait.archetype)
        val expected = ShapeId.from("smithy.kotlin.synthetic#FooRequest")
        assertEquals(expected, input.id)
    }

    @Test
    fun `it clones operation outputs`() {
        val model = """
            namespace smithy.test
            operation Foo {
                output: MyOutput
            }
            
            structure MyOutput {
                v: String
            }
        """.asSmithyModel()
        val origId = ShapeId.from("smithy.test#MyOutput")
        val normalized = OperationNormalizer().transform(model)

        val op = normalized.expectShape<OperationShape>("smithy.test#Foo")

        normalized.expectShape<StructureShape>(op.input.get())
        val output = normalized.expectShape<StructureShape>(op.output.get())

        val syntheticTrait = output.expectTrait<SyntheticClone>()
        assertEquals(origId, syntheticTrait.archetype)
        val expected = ShapeId.from("smithy.kotlin.synthetic#FooResponse")
        assertEquals(expected, output.id)
    }

    @Test
    fun `it does not modify non operational shapes`() {
        val model = """
            namespace smithy.test
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
        """.asSmithyModel()
        val normalized = OperationNormalizer().transform(model)
        val expected = ShapeId.from("smithy.test#Nested")
        normalized.expectShape<StructureShape>(expected)

        val op = normalized.expectShape<OperationShape>("smithy.test#Foo")
        val output = normalized.expectShape<StructureShape>(op.output.get())
        assertEquals(expected, output.getMember("nested").get().target)
    }
}
