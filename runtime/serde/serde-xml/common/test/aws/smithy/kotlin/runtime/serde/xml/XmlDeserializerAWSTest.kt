/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerAWSTest {

    class HostedZoneConfig private constructor(builder: Builder) {
        val comment: String? = builder.comment

        companion object {
            val COMMENT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("Comment"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("HostedZoneConfig"))
                trait(XmlNamespace("https://route53.amazonaws.com/doc/2013-04-01/"))
                field(COMMENT_DESCRIPTOR)
            }

            suspend fun deserialize(deserializer: Deserializer): HostedZoneConfig {
                val builder = Builder()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            COMMENT_DESCRIPTOR.index -> builder.comment = deserializeString()
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                            }
                            else -> throw DeserializationException(IllegalStateException("unexpected field index in HostedZoneConfig deserializer"))
                        }
                    }
                }
                return HostedZoneConfig(builder)
            }

            operator fun invoke(block: Builder.() -> Unit) = Builder().apply(block).build()
        }

        public class Builder {
            var comment: String? = null

            fun build(): HostedZoneConfig = HostedZoneConfig(this)
        }
    }

    class CreateHostedZoneRequest private constructor(builder: Builder) {
        val name: String? = builder.name
        val callerReference: String? = builder.callerReference
        val hostedZoneConfig: HostedZoneConfig? = builder.hostedZoneConfig

        companion object {
            val NAME_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("Name"))
            val CALLER_REFERENCE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("CallerReference"))
            val HOSTED_ZONE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("HostedZoneConfig"))

            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("CreateHostedZoneRequest"))
                trait(XmlNamespace("https://route53.amazonaws.com/doc/2013-04-01/"))
                field(NAME_DESCRIPTOR)
                field(CALLER_REFERENCE_DESCRIPTOR)
                field(HOSTED_ZONE_DESCRIPTOR)
            }

            suspend fun deserialize(deserializer: Deserializer): CreateHostedZoneRequest {
                val builder = Builder()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            NAME_DESCRIPTOR.index -> builder.name = deserializeString()
                            CALLER_REFERENCE_DESCRIPTOR.index -> builder.callerReference = deserializeString()
                            HOSTED_ZONE_DESCRIPTOR.index ->
                                builder.hostedZoneConfig = HostedZoneConfig.deserialize(deserializer)
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> skipValue()
                            else -> throw DeserializationException(IllegalStateException("unexpected field index in CreateHostedZoneRequest deserializer"))
                        }
                    }
                }
                return builder.build()
            }

            operator fun invoke(block: Builder.() -> Unit) = Builder().apply(block).build()
        }

        public class Builder {
            var name: String? = null
            var callerReference: String? = null
            var hostedZoneConfig: HostedZoneConfig? = null

            fun build(): CreateHostedZoneRequest = CreateHostedZoneRequest(this)
        }
    }

    @Test
    fun itHandlesRoute53XML() = runSuspendTest {
        val testXml = """
               <?xml version="1.0" encoding="UTF-8"?><!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0.
                 -->

               <CreateHostedZoneRequest xmlns="https://route53.amazonaws.com/doc/2013-04-01/">
                   <Name>java.sdk.com.</Name>
                   <CallerReference>a322f752-8156-4746-8c04-e174ca1f51ce</CallerReference>
                   <HostedZoneConfig>
                       <Comment>comment</Comment>
                   </HostedZoneConfig>
               </CreateHostedZoneRequest>
        """.trimIndent()

        val unit = XmlDeserializer(testXml.encodeToByteArray())

        val createHostedZoneRequest = CreateHostedZoneRequest.deserialize(unit)

        assertTrue(createHostedZoneRequest.name == "java.sdk.com.")
        assertTrue(createHostedZoneRequest.callerReference == "a322f752-8156-4746-8c04-e174ca1f51ce")
        assertNotNull(createHostedZoneRequest.hostedZoneConfig)
        assertTrue(createHostedZoneRequest.hostedZoneConfig.comment == "comment")
    }
}
