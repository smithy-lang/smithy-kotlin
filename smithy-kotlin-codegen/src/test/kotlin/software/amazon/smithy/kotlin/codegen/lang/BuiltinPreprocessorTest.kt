/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContainAll
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.asSmithyModel
import software.amazon.smithy.kotlin.codegen.smithy.expectShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId

class BuiltinPreprocessorTest {
    @Test
    fun itRenamesBuiltins() {
        val model = """
        namespace com.test
        
        service FooService {
            version: "1.0.0"
        }
        
        structure Foo {
            Unit: Unit,
            Str: String
        }
        
        structure Unit {
            Int: Integer
        }
        
        union MyUnion {
            Integer: Integer,
            String: String,
            Unit: Unit
        }
        """.asSmithyModel()

        val settings = KotlinSettings(
            ShapeId.from("com.test#FooService"),
            "test",
            "1.0",
            "",
            "Foo"
        )

        val integration = BuiltinPreprocessor()
        val modified = integration.preprocessModel(model, settings)

        val originalShapeIds = setOf(
            "com.test#Unit",
            "smithy.api#FooString" // should not rename builtins
        ).map(ShapeId::from)
        modified.shapeIds.shouldNotContainAll(originalShapeIds)

        val newOrUnmodifiedShapeIds = setOf(
            // renamed
            "com.test#FooUnit",
            "com.test#FooUnit\$Int",
            // unmodified
            "com.test#Foo\$Unit",
            "com.test#MyUnion\$Integer",
            "com.test#MyUnion\$String",
            "com.test#MyUnion\$Unit",
            "smithy.api#String"
        ).map(ShapeId::from)
        modified.shapeIds.shouldContainAll(newOrUnmodifiedShapeIds)

        // verify member target can be retrieved after rename
        val fooUnionMember = modified.expectShape<MemberShape>("com.test#Foo\$Unit")
        modified.expectShape(fooUnionMember.target)
    }
}
