/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerNamespaceTest {

    @Test
    fun `it handles basic structs with attribs`() {
        val payload = """
               <?xml version="1.0" encoding="UTF-8"?>
               <!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0.
                 -->
                
               <payload>
                    <ns1:x xmlns:ns1="https://uri1" value="value from ns1" />
                    <ns2:x xmlns:ns2="https://uri2" value="value from ns2" />
               </payload>
        """.trimIndent().encodeToByteArray()

        val deserializer = XmlDeserializer2(payload)
        val bst = StructWithAttribsClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    class NamespaceStructTest {
        var ns1v: String? = null
        var ns2v: String? = null

        companion object {
            val NS1_X_VALUE_DESCRIPTOR = SdkFieldDescriptor("ns1:x", SerialKind.Integer, 0)
            val NS2_X_VALUE_DESCRIPTOR = SdkFieldDescriptor("ns2:x", SerialKind.Integer, 0)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "payload"
                field(NS1_X_VALUE_DESCRIPTOR)
                field(NS2_X_VALUE_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): NamespaceStructTest {
                val result = NamespaceStructTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            NS1_X_VALUE_DESCRIPTOR.index -> result.ns1v = deserializeString()
                            NS2_X_VALUE_DESCRIPTOR.index -> result.ns2v = deserializeString()
                            null -> break@loop
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                        }
                    }
                }
                return result
            }
        }
    }
}
