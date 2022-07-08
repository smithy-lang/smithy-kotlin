/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.shapes.CollectionShape
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.UniqueItemsTrait
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SetRefactorPreprocessorTest {
    @Test
    fun itRefactorsSetShapes() {
        val model = """
            namespace com.test
            
            service FooService {
                version: "1.0.0"
            }
            
            set OldSet {
                member: String
            }
            
            @uniqueItems
            list NewSet {
                member: String
            }
            
            list RegularList {
                member: String
            }
        """.toSmithyModel()

        fun assertStringMember(shape: CollectionShape) {
            val memberShape = model.expectShape(shape.member.target)
            assertTrue(memberShape.isStringShape, "Expected member to have String type")
        }

        val settings = KotlinSettings(
            ShapeId.from("com.test#FooService"),
            KotlinSettings.PackageSettings(
                "test",
                "1.0",
                "",
            ),
            "Foo"
        )

        val integration = SetRefactorPreprocessor()
        val modified = integration.preprocessModel(model, settings)

        val oldSet = modified.expectShape(ShapeId.from("com.test#OldSet"))
        assertIs<ListShape>(oldSet)
        assertTrue(oldSet.hasTrait<UniqueItemsTrait>(), "Expected old set to have @uniqueItems trait")
        assertStringMember(oldSet)

        val newSet = modified.expectShape(ShapeId.from("com.test#NewSet"))
        assertIs<ListShape>(newSet)
        assertTrue(newSet.hasTrait<UniqueItemsTrait>(), "Expected new set to have @uniqueItems trait")
        assertStringMember(newSet)

        val regularList = modified.expectShape(ShapeId.from("com.test#RegularList"))
        assertIs<ListShape>(regularList)
        assertFalse(regularList.hasTrait<UniqueItemsTrait>(), "Expected regular list not to have @uniqueItems trait")
        assertStringMember(regularList)
    }
}
