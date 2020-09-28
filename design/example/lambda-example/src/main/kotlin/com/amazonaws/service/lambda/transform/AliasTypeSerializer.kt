package com.amazonaws.service.lambda.transform

import com.amazonaws.service.lambda.model.*
import software.aws.clientrt.http.feature.HttpSerialize
import software.aws.clientrt.http.feature.SerializationProvider
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.serde.*

class AliasTypeSerializer(val input: AliasType) : SdkSerializable {

    companion object {
        private val EXPIRING_ALIAS_TYPE_FIELD_DESCRIPTOR = SdkFieldDescriptor("ExpiringAliasType", SerialKind.String)
        private val REMOTE_ALIAS_TYPE_FIELD_DESCRIPTOR = SdkFieldDescriptor("RemoteAliasType", SerialKind.Long)

        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            serialName = "AliasType"
            field(EXPIRING_ALIAS_TYPE_FIELD_DESCRIPTOR)
            field(REMOTE_ALIAS_TYPE_FIELD_DESCRIPTOR)
        }
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct(OBJ_DESCRIPTOR) {
            when (input) {
                is ExpiringAliasType -> field(EXPIRING_ALIAS_TYPE_FIELD_DESCRIPTOR, input.value!!)
                is RemoteAliasType -> field(REMOTE_ALIAS_TYPE_FIELD_DESCRIPTOR, input.value!!)
            }
        }
    }
}