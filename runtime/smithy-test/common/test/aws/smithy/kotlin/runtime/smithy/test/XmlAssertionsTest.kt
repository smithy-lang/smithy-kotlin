/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.smithy.test

import aws.smithy.kotlin.runtime.serde.xml.dom.XmlNode
import aws.smithy.kotlin.runtime.serde.xml.dom.toXmlString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class XmlAssertionsTest {

    @Test
    fun testToCanonicalForm() = runTest {
        val input = """
        <Foo>
            <C>
                <Nested></Nested>
            </C>        
            <B>baz</B>        
            <B></B>        
            <A>baz</A>        
            <A>foo</A>        
            <A>bar</A>        
            <C>
                <Nested>
                    <X>i think xml</X>
                    <Y></Y>
                    <X>is</X>
                    <X>not</X>
                    <X>fun,</X>
                </Nested>
            </C>
        </Foo>
        """

        val dom = XmlNode.parse(input.encodeToByteArray())
        dom.toCanonicalForm()
        val actual = dom.toXmlString(true)
        val expected = """<Foo>
    <A>bar</A>
    <A>baz</A>
    <A>foo</A>
    <B></B>
    <B>baz</B>
    <C>
        <Nested></Nested>
    </C>
    <C>
        <Nested>
            <X>fun,</X>
            <X>i think xml</X>
            <X>is</X>
            <X>not</X>
            <Y></Y>
        </Nested>
    </C>
</Foo>"""
        assertEquals(expected, actual)
    }

    @Test
    fun testToCanonicalFormFlatMap() = runTest {
        // test when the original insertion order parsed/constructed is different

        /*
        structure Foo {
            @xmlFlattened
            A: MyMap
        }

        map MyMap {
            key: String,
            value: String
        }
         */

        val input1 = """
        <Foo>
            <A>
                <key>b2</key>
                <value>v1</value>
            </A>
            <A>
                <key>b4</key>
                <value>v3</value>
            </A>
        </Foo>
        """

        val dom1 = XmlNode.parse(input1.encodeToByteArray())
        dom1.toCanonicalForm()
        val actual1 = dom1.toXmlString(true)

        val input2 = """
        <Foo>
            <A>
                <key>b4</key>
                <value>v3</value>
            </A>
            <A>
                <key>b2</key>
                <value>v1</value>
            </A>
        </Foo>
        """

        val dom2 = XmlNode.parse(input2.encodeToByteArray())
        dom2.toCanonicalForm()
        val actual2 = dom2.toXmlString(true)

        assertEquals(actual1, actual2)
    }

    @Test
    fun testToCanonicalWrappedMap() = runTest {
        /*
        structure Foo {
            A: MyMap
        }

        map MyMap {
            key: String,
            value: String
        }
         */

        val input1 = """
        <Foo>
            <A>
                <entry>
                    <key>b2</key>
                    <value>v1</value>
                </entry>
                
                <entry>
                    <key>b4</key>
                    <value>v3</value>
                </entry>
            </A>
        </Foo>
        """

        val dom1 = XmlNode.parse(input1.encodeToByteArray())
        dom1.toCanonicalForm()
        val actual1 = dom1.toXmlString(true)

        val input2 = """
        <Foo>
            <A>
                <entry>
                    <key>b4</key>
                    <value>v3</value>
                </entry>
                <entry>
                    <key>b2</key>
                    <value>v1</value>
                </entry>
            </A>
        </Foo>
        """

        val dom2 = XmlNode.parse(input2.encodeToByteArray())
        dom2.toCanonicalForm()
        val actual2 = dom2.toXmlString(true)

        assertEquals(actual1, actual2)
    }

    @Test
    fun testToCanonicalList() = runTest {
        /*
            structure Foo {
                values: MyList
            }

            list MyList {
                member: String,
            }
         */

        val input1 = """
        <Foo>
            <values>
                <member>example1</member>
                <member>example2</member>
                <member>example3</member>
            </values>
        </Foo>
        """

        val dom1 = XmlNode.parse(input1.encodeToByteArray())
        dom1.toCanonicalForm()
        val actual1 = dom1.toXmlString(true)

        val input2 = """
        <Foo>
            <values>
                <member>example3</member>
                <member>example2</member>
                <member>example1</member>
            </values>
        </Foo>
        """

        val dom2 = XmlNode.parse(input2.encodeToByteArray())
        dom2.toCanonicalForm()
        val actual2 = dom2.toXmlString(true)

        assertEquals(actual1, actual2)
    }

    @Test
    fun itAssertsKitchenSink() = runTest {
        val expected = """
        <Foo>
            <String>v1</String>
            <Int>1</Int>
            <Float>2.17</Float>
            <Bool>true</Bool>
            <List>
                <member>1</member>
                <member>2</member>
                <member>3</member>
            </List>
            
            <Null></Null>
            
            <Struct>
                <K1>v1</K1>
                <K2>
                    <member>v1</member>
                </K2>
                <K3>
                    <Nested>v2</Nested>
                </K3>
            </Struct>
            
            <List2>
                <member>
                    <MyMap>
                        <key>kl1</key>
                        <value>vl1</value>
                    </MyMap>
                    <MyMap>
                        <key>kl2</key>
                        <value>vl2</value>
                    </MyMap>
                </member> 
            </List2>
        </Foo>
        """.trimIndent()

        val actual = """
        <Foo>
            <Bool>true</Bool>
            
            <Null></Null>
            <Float>2.17</Float>
            <Int>1</Int>
            <List>
                <member>1</member>
                <member>2</member>
                <member>3</member>
            </List>
            
            <List2>
                <member>
                    <MyMap>
                        <key>kl2</key>
                        <value>vl2</value>
                    </MyMap>
                    <MyMap>
                        <key>kl1</key>
                        <value>vl1</value>
                    </MyMap>
                </member> 
            </List2>
            
            <Struct>
                <K1>v1</K1>
                <K2>
                    <member>v1</member>
                </K2>
                <K3>
                    <Nested>v2</Nested>
                </K3>
            </Struct>
            
            
            <String>v1</String>
        </Foo>
        """.trimIndent()

        assertXmlStringsEqual(expected, actual)
    }

    @Test
    fun itFailsUnequalXml() = runTest {
        val expected = """
        <Foo>
            <String>v1</String>            
        </Foo>
        """.trimIndent()

        val actual = """
        <Foo>
            <Bool>true</Bool>            
        </Foo>
        """.trimIndent()

        assertFails {
            assertXmlStringsEqual(expected, actual)
        }

        Unit
    }
}
