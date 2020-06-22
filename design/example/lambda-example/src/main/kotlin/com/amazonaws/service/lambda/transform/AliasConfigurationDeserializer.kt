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
package com.amazonaws.service.lambda.transform

import com.amazonaws.service.lambda.model.AliasConfiguration
import com.amazonaws.service.runtime.HttpDeserialize
import software.aws.clientrt.http.response.HttpResponse
import software.aws.clientrt.serde.*

class AliasConfigurationDeserializer: HttpDeserialize {
    companion object {
        private val ALIAS_ARN_FIELD_DESCRIPTOR = SdkFieldDescriptor("AliasArn")
        private val DESCRIPTION_FIELD_DESCRIPTOR = SdkFieldDescriptor("Description")
        private val FUNCTION_VERSION_FIELD_DESCRIPTOR = SdkFieldDescriptor("FunctionVersion")
        private val NAME_FIELD_DESCRIPTOR = SdkFieldDescriptor("Name")
        private val REVISION_ID_FIELD_DESCRIPTOR = SdkFieldDescriptor("RevisionId")
        private val ROUTING_CONFIG_FIELD_DESCRIPTOR = SdkFieldDescriptor("RoutingConfig")

        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(ALIAS_ARN_FIELD_DESCRIPTOR)
            field(DESCRIPTION_FIELD_DESCRIPTOR)
            field(FUNCTION_VERSION_FIELD_DESCRIPTOR)
            field(NAME_FIELD_DESCRIPTOR)
            field(REVISION_ID_FIELD_DESCRIPTOR)
            field(ROUTING_CONFIG_FIELD_DESCRIPTOR)
        }
    }

    override suspend fun deserialize(response: HttpResponse, deserializer: Deserializer): AliasConfiguration {
        val builder = AliasConfiguration.dslBuilder()
        // FIXME - expected response is 201, need to plug in error handling middleware as well as check for
        //  the specific code here (or pass it to the pipeline as metadata for a feature to check)

        deserializer.deserializeStruct(null) {
            loop@while(true) {
                when(nextField(OBJ_DESCRIPTOR)) {
                    ALIAS_ARN_FIELD_DESCRIPTOR.index -> builder.aliasArn = deserializeString()
                    DESCRIPTION_FIELD_DESCRIPTOR.index -> builder.description = deserializeString()
                    FUNCTION_VERSION_FIELD_DESCRIPTOR.index -> builder.functionVersion = deserializeString()
                    NAME_FIELD_DESCRIPTOR.index -> builder.name = deserializeString()
                    REVISION_ID_FIELD_DESCRIPTOR.index -> builder.revisionId = deserializeString()
                    ROUTING_CONFIG_FIELD_DESCRIPTOR.index -> builder.routingConfig = AliasRoutingConfigurationDeserializer.deserialize(deserializer)
                    Deserializer.FieldIterator.EXHAUSTED -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}
