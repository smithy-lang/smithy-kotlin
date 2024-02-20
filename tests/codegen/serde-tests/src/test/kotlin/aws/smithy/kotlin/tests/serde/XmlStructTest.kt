/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.tests.serde

import aws.smithy.kotlin.runtime.content.BigInteger
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.tests.serde.xml.model.FooEnum
import aws.smithy.kotlin.tests.serde.xml.model.IntegerEnum
import aws.smithy.kotlin.tests.serde.xml.model.StructType
import aws.smithy.kotlin.tests.serde.xml.serde.deserializeStructTypeDocument
import aws.smithy.kotlin.tests.serde.xml.serde.serializeStructTypeDocument
import java.math.BigDecimal
import kotlin.test.Test

class XmlStructTest : AbstractXmlTest() {
    @Test
    fun testStructPrimitives() {
        val expected = StructType {
            strField = "a string"
            byteField = 2.toByte()
            intField = 3
            shortField = 4
            longField = 5L
            floatField = 6.0f
            doubleField = 7.1
            bigIntegerField = BigInteger("1234")
            bigDecimalField = BigDecimal("1.234")
            boolField = true
            blobField = "blob field".encodeToByteArray()
            enumField = FooEnum.Bar
            intEnumField = IntegerEnum.C
            dateTimeField = Instant.fromIso8601("2020-10-16T15:46:24.982Z")
            epochTimeField = Instant.fromEpochSeconds(1657204347)
            httpTimeField = Instant.fromRfc5322("Sat, 22 Jul 2017 19:30:00 GMT")
            extra = 42
        }

        val base64BlobField = expected.blobField!!.encodeBase64String()

        val payload = """
            <StructType extra="42">
                <strField>a string</strField>
                <byteField>2</byteField>
                <intField>3</intField>
                <shortField>4</shortField>
                <longField>5</longField>
                <floatField>6.0</floatField>
                <doubleField>7.1</doubleField>
                <bigIntegerField>1234</bigIntegerField>
                <bigDecimalField>1.234</bigDecimalField>
                <boolField>true</boolField>
                <blobField>$base64BlobField</blobField>
                <enumField>Bar</enumField>
                <intEnumField>3</intEnumField>
                <dateTimeField>2020-10-16T15:46:24.982Z</dateTimeField>
                <epochTimeField>1657204347</epochTimeField>
                <httpTimeField>Sat, 22 Jul 2017 19:30:00 GMT</httpTimeField>
            </StructType>
        """.trimIndent()

        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testRenamedMembers() {
        val expected = StructType {
            renamedWithPrefix = "foo"
            flatList = listOf("bar", "baz")
        }
        val payload = """
            <StructType>
                <prefix:local>foo</prefix:local>
                <flatlist1>bar</flatlist1>
                <flatlist1>baz</flatlist1>
            </StructType>
        """.trimIndent()
        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }

    @Test
    fun testRecursiveType() {
        val expected = StructType {
            strField = "first"
            recursive {
                strField = "second"
                extra = 42
                recursive {
                    strField = "third"
                    normalList = listOf("foo", "bar")
                }
            }
        }
        val payload = """
            <StructType>
                <strField>first</strField>
                <recursive extra="42">
                    <strField>second</strField>
                    <recursive>
                        <strField>third</strField>
                        <normalList>
                            <member>foo</member>
                            <member>bar</member>
                        </normalList>
                    </recursive>
                </recursive>
            </StructType>
        """.trimIndent()

        testRoundTrip(expected, payload, ::serializeStructTypeDocument, ::deserializeStructTypeDocument)
    }
}
