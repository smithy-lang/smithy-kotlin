/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.serde.xml

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import software.aws.clientrt.serde.*

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerAWSTest {

    class HostedZoneConfig private constructor(builder: BuilderImpl) {
        val comment: String? = builder.comment

        companion object {
            val COMMENT_DESCRIPTOR = SdkFieldDescriptor("Comment", SerialKind.String)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "HostedZoneConfig"
                field(COMMENT_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): HostedZoneConfig {
                val builder = BuilderImpl()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            COMMENT_DESCRIPTOR.index -> builder.comment = deserializeString()
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                            }
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field index in HostedZoneConfig deserializer"))
                        }
                    }
                }
                return HostedZoneConfig(builder)
            }

            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
        }

        interface Builder {
            fun build(): HostedZoneConfig
            // TODO - Java fill in Java builder
        }

        interface DslBuilder {
            var comment: String?
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var comment: String? = null

            override fun build(): HostedZoneConfig = HostedZoneConfig(this)
        }
    }

    class CreateHostedZoneRequest private constructor(builder: BuilderImpl) {
        val name: String? = builder.name
        val callerReference: String? = builder.callerReference
        val hostedZoneConfig: HostedZoneConfig? = builder.hostedZoneConfig

        companion object {
            val NAME_DESCRIPTOR = SdkFieldDescriptor("Name", SerialKind.String)
            val CALLER_REFERENCE_DESCRIPTOR = SdkFieldDescriptor("CallerReference", SerialKind.String)
            val HOSTED_ZONE_DESCRIPTOR = SdkFieldDescriptor("HostedZoneConfig", SerialKind.Struct)

            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "CreateHostedZoneRequest"
                field(NAME_DESCRIPTOR)
                field(CALLER_REFERENCE_DESCRIPTOR)
                field(HOSTED_ZONE_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): CreateHostedZoneRequest {
                val builder = BuilderImpl()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            NAME_DESCRIPTOR.index -> builder.name = deserializeString()
                            CALLER_REFERENCE_DESCRIPTOR.index -> builder.callerReference = deserializeString()
                            HOSTED_ZONE_DESCRIPTOR.index -> builder.hostedZoneConfig =
                                HostedZoneConfig.deserialize(deserializer)
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> skipValue()
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field index in CreateHostedZoneRequest deserializer"))
                        }
                    }
                }
                return builder.build()
            }

            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
        }

        interface Builder {
            fun build(): CreateHostedZoneRequest
            // TODO - Java fill in Java builder
        }

        interface DslBuilder {
            var name: String?
            var callerReference: String?
            var hostedZoneConfig: HostedZoneConfig?
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var name: String? = null
            override var callerReference: String? = null
            override var hostedZoneConfig: HostedZoneConfig? = null

            override fun build(): CreateHostedZoneRequest = CreateHostedZoneRequest(this)
        }
    }

    @Test
    fun `it handles Route 53 XML`() {
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
