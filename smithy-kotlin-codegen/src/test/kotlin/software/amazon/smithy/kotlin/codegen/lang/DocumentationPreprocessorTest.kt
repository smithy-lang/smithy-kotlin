/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.DocumentationTrait
import kotlin.test.assertEquals

class DocumentationPreprocessorTest {
    @Test
    fun itEscapesSquareBrackets() {
        // https://github.com/awslabs/aws-sdk-kotlin/issues/153
        val model = """
        namespace com.test
        
        service FooService {
            version: "1.0.0"
        }
        
        @documentation("This should not be modified")
        structure Foo {
            @documentation("member docs")
            Unit: Unit,
            Str: String
        }
        
        @documentation("UserName@[SubDomain.]Domain.TopLevelDomain")
        structure Unit {
            Int: Integer
        }
        
        union MyUnion {
            @documentation("foo [bar [baz] qux] quux")
            Integer: Integer,
            String: String,
            Unit: Unit
        }
        """.toSmithyModel()

        val settings = KotlinSettings(
            ShapeId.from("com.test#FooService"),
            KotlinSettings.PackageSettings(
                "test",
                "1.0",
                ""
            ),
            "Foo"
        )

        val integration = DocumentationPreprocessor()
        val modified = integration.preprocessModel(model, settings)
        val expectedDocs = listOf(
            "com.test#Foo" to "This should not be modified",
            "com.test#Foo\$Unit" to "member docs",
            "com.test#Unit" to "UserName@&#91;SubDomain.&#93;Domain.TopLevelDomain",
            "com.test#MyUnion\$Integer" to "foo &#91;bar &#91;baz&#93; qux&#93; quux",
        )
        expectedDocs.forEach { (shapeId, expected) ->
            val shape = modified.expectShape(ShapeId.from(shapeId))
            val docs = shape.expectTrait<DocumentationTrait>().value
            assertEquals(expected, docs)
        }
    }
}
