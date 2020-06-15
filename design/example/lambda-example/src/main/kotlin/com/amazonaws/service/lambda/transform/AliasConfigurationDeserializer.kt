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
import com.amazonaws.service.runtime.Deserializer
import com.amazonaws.service.runtime.HttpDeserialize
import com.amazonaws.service.runtime.readAll
import software.aws.clientrt.http.isSuccess
import software.aws.clientrt.http.response.HttpResponse

class AliasConfigurationDeserializer: HttpDeserialize {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun deserialize(response: HttpResponse, deserializer: Deserializer): AliasConfiguration {
        // FIXME - expected response is 201, need to plug in error handling middleware as well as check for
        //  the specific code here (or pass it to the pipeline as metadata for a feature to check)
        println("AliasConfiguration::deserialize: response is success: ${response.status.isSuccess()}")
        val body = response.body.readAll()?.decodeToString()
        println("recv'd: $body")

        // FIXME - need the deserializer implementation to do anything useful here...
        return AliasConfiguration { }
    }
}
