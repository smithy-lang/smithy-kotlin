/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import software.amazon.smithy.kotlin.codegen.util.asSmithy
import software.amazon.smithy.kotlin.codegen.util.compileSdkAndTest
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compiles a generated SDK and checks the runtime `toString()` of unions with sensitive variants
 */
@OptIn(ExperimentalCompilerApi::class)
class SensitiveUnionTest {
    private val model = """
        namespace com.test

        use aws.protocols#restJson1

        @restJson1
        service Example {
            version: "1.0.0",
            operations: [GetHolder]
        }

        @http(method: "GET", uri: "/holder")
        operation GetHolder {
            output := {
                holder: Holder
            }
        }

        @sensitive
        string SecretString

        list SecretList { member: SecretString }

        structure StructWithSecret {
            name: String
            password: SecretString
        }

        union MyUnion {
            secret: SecretString
            secretList: SecretList
            struct: StructWithSecret
            plain: String
        }

        list UnionList { member: MyUnion }

        structure Holder {
            single: MyUnion
            many: UnionList
        }
    """.asSmithy()

    private val testSource = """
        package com.test.probe

        import test.model.*

        public fun holderToString(): String = Holder {
            single = MyUnion.Secret("hunter2")
            many = listOf(
                MyUnion.SecretList(listOf("hunter3")),
                MyUnion.Struct(StructWithSecret { name = "alice"; password = "hunter4" }),
                MyUnion.Plain("visible"),
            )
        }.toString()
    """.trimIndent()

    @Test
    fun `it redacts sensitive union variants held by structures and lists`() {
        val compileOutput = ByteArrayOutputStream()
        val result = compileSdkAndTest(model, testSource, compileOutput, Debug.emitSourcesToTemp)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, compileOutput.toString())

        val actual = result.classLoader
            .loadClass("com.test.probe.TestKt")
            .getMethod("holderToString")
            .invoke(null)

        val redacted = "*** Sensitive Data Redacted ***"
        val expected = "Holder(" +
            "many=[SecretList($redacted), Struct(value=StructWithSecret(name=alice,password=$redacted)), Plain(value=visible)]," +
            "single=Secret($redacted))"
        assertEquals(expected, actual)
    }
}
