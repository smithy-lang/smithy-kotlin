/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*

class SimpleStructClass {
    var x: Int? = null
    var y: Int? = null
    var z: String? = null

    // Only for testing, not serialization
    var unknownFieldCount: Int = 0

    companion object {
        val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"))
        val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("y"))
        val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("z"), XmlAttribute("attribval"))
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("payload"))
            field(X_DESCRIPTOR)
            field(Y_DESCRIPTOR)
            field(Z_DESCRIPTOR)
        }

        fun deserialize(deserializer: Deserializer): SimpleStructClass {
            val result = SimpleStructClass()
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@ while (true) {
                    when (findNextFieldIndex()) {
                        X_DESCRIPTOR.index -> result.x = deserializeInt()
                        Y_DESCRIPTOR.index -> result.y = deserializeInt()
                        Z_DESCRIPTOR.index -> result.z = deserializeString()
                        null -> break@loop
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
        val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"), XmlAttribute("value"))
        val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("y"), XmlAttribute("value"))
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("payload"))
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

class StructWithMultiAttribsAndTextValClass {
    var x: Int? = null
    var y: Int? = null
    var txt: String? = null
    var unknownFieldCount: Int = 0

    companion object {
        val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"), XmlAttribute("xval"))
        val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"), XmlAttribute("yval"))
        val TXT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"))
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("payload"))
            field(TXT_DESCRIPTOR)
            field(X_DESCRIPTOR)
            field(Y_DESCRIPTOR)
        }

        fun deserialize(deserializer: Deserializer): StructWithMultiAttribsAndTextValClass {
            val result = StructWithMultiAttribsAndTextValClass()
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@ while (true) {
                    when (findNextFieldIndex()) {
                        X_DESCRIPTOR.index -> result.x = deserializeInt()
                        Y_DESCRIPTOR.index -> result.y = deserializeInt()
                        TXT_DESCRIPTOR.index -> result.txt = deserializeString()
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