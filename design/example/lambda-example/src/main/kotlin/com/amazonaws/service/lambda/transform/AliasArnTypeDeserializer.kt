package com.amazonaws.service.lambda.transform

import com.amazonaws.service.lambda.model.AliasArnType
import com.amazonaws.service.lambda.model.EC2ArnType
import com.amazonaws.service.lambda.model.MultiArnType
import com.amazonaws.service.lambda.model.S3ArnType
import software.aws.clientrt.serde.*

class AliasArnTypeDeserializer {

    companion object {
        private val ALIAS_S3_ARN_FIELD_DESCRIPTOR = SdkFieldDescriptor("S3AliasArn", SerialKind.String)
        private val ALIAS_EC2_ARN_FIELD_DESCRIPTOR = SdkFieldDescriptor("EC2AliasArn", SerialKind.Integer)
        private val ALIAS_MULTI_ARN_FIELD_DESCRIPTOR = SdkFieldDescriptor("MultiAliasArn", SerialKind.Integer)

        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            serialName = "AliasArn"
            field(ALIAS_S3_ARN_FIELD_DESCRIPTOR)
            field(ALIAS_EC2_ARN_FIELD_DESCRIPTOR)
        }

        fun deserialize(deserializer: Deserializer): AliasArnType? {
            var aliasArnType: AliasArnType? = null

            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                when(findNextFieldIndex()) {
                    ALIAS_S3_ARN_FIELD_DESCRIPTOR.index -> aliasArnType = S3ArnType(deserializeString()!!)
                    ALIAS_EC2_ARN_FIELD_DESCRIPTOR.index -> aliasArnType = EC2ArnType(deserializeInt()!!)
                    ALIAS_MULTI_ARN_FIELD_DESCRIPTOR.index -> aliasArnType = deserializer.deserializeList(ALIAS_MULTI_ARN_FIELD_DESCRIPTOR) {
                        val list0 = mutableListOf<String>()
                        while(hasNextElement()) {
                            list0.add(deserializeString()!!)
                        }
                        MultiArnType(list0)
                    }
                    else -> null
                }
            }

            return aliasArnType
        }
    }
}