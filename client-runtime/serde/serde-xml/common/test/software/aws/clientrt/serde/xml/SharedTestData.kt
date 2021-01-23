/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class SimpleStructClass {
    var x: Int? = null
    var y: Int? = null

    // Only for testing, not serialization
    var unknownFieldCount: Int = 0

    companion object {
        val X_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer)
        val Y_DESCRIPTOR = SdkFieldDescriptor("y", SerialKind.Integer)
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            serialName = "payload"
            field(X_DESCRIPTOR)
            field(Y_DESCRIPTOR)
        }

        fun deserialize(deserializer: Deserializer): SimpleStructClass {
            val result = SimpleStructClass()
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@ while (true) {
                    when (findNextFieldIndex()) {
                        X_DESCRIPTOR.index -> result.x = deserializeInt()
                        Y_DESCRIPTOR.index -> result.y = deserializeInt()
                        null -> break@loop
                        Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                            result.unknownFieldCount++
                            skipValue()
                        }
                        else -> throw XmlGenerationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                    }
                }
            }
            return result
        }
    }
}

class StructWithAttribsClass {
    var x: Int? = null
    var y: Int? = null
    var unknownFieldCount: Int = 0

    companion object {
        val X_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer, 0, XmlAttribute("value"))
        val Y_DESCRIPTOR = SdkFieldDescriptor("y", SerialKind.Integer, 0, XmlAttribute("value"))
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            serialName = "payload"
            field(X_DESCRIPTOR)
            field(Y_DESCRIPTOR)
        }

        fun deserialize(deserializer: Deserializer): StructWithAttribsClass {
            val result = StructWithAttribsClass()
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@ while (true) {
                    when (findNextFieldIndex()) {
                        X_DESCRIPTOR.index -> result.x = deserializeInt()
                        Y_DESCRIPTOR.index -> result.y = deserializeInt()
                        null -> break@loop
                        Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                            result.unknownFieldCount++
                            skipValue()
                        }
                        else -> throw XmlGenerationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                    }
                }
            }
            return result
        }
    }
}
