package com.amazonaws.service.lambda.transform

import com.amazonaws.service.lambda.model.AliasArnType
import com.amazonaws.service.lambda.model.EC2ArnType
import com.amazonaws.service.lambda.model.S3ArnType
import software.aws.clientrt.serde.*

class AliasArnTypeSerializer(val input: AliasArnType) : SdkSerializable {

    companion object {
        private val ALIAS_TYPE_S3_TYPE_FIELD_DESCRIPTOR = SdkFieldDescriptor("S3ArnType", SerialKind.String)
        private val ALIAS_TYPE_EC2_TYPE_FIELD_DESCRIPTOR = SdkFieldDescriptor("EC2ArnType", SerialKind.Long)

        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            serialName = "AliasArnType"
            field(ALIAS_TYPE_S3_TYPE_FIELD_DESCRIPTOR)
            field(ALIAS_TYPE_EC2_TYPE_FIELD_DESCRIPTOR)
        }
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(OBJ_DESCRIPTOR) {
            when (input) {
                is S3ArnType -> field(ALIAS_TYPE_S3_TYPE_FIELD_DESCRIPTOR, input.value!!)
                is EC2ArnType -> field(ALIAS_TYPE_EC2_TYPE_FIELD_DESCRIPTOR, input.value!!)
            }
        }
    }

}