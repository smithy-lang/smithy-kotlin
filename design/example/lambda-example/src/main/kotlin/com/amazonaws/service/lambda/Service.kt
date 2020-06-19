package com.amazonaws.service.lambda

import com.amazonaws.service.runtime.*
import com.amazonaws.service.lambda.model.*
import com.amazonaws.service.lambda.transform.*
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.http.*
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.engine.ktor.KtorEngine

class LambdaClient: SdkClient {
    override val serviceName: String = "lambda"

    private val client: SdkHttpClient

    init {
        val config = HttpClientEngineConfig()
        client = sdkHttpClient(KtorEngine(config)) {
            install(HttpSerde) {
                serdeProvider = JsonSerdeProvider()
            }

            // request defaults
            install(DefaultRequest) {
                url.scheme = Protocol.HTTP
                url.host = "127.0.0.1"
                url.port = 8000
            }

            // this is what will be installed by the generic smithy-kotlin codegenerator
            install(DefaultValidateResponse)
        }
    }

    /**
     * @throws ResourceNotFoundException
     * @throws TooManyRequestsException
     * @throws InvalidParameterValueException
     * @throws Ec2AccessDeniedException
     * @throws KmsAccessDeniedException
     * @throws ClientException
     * @throws ServiceException
     */
    suspend fun invoke(input: InvokeRequest): InvokeResponse {
        return client.roundTrip(InvokeRequestSerializer(input), InvokeResponseDeserializer())
    }

    suspend fun invoke(block: InvokeRequest.DslBuilder.() -> Unit): InvokeResponse {
        val input = InvokeRequest{ block(this) }
        return invoke(input)
    }


    /**
     * @throws InvalidParameterValueException
     * @throws ClientException
     * @throws ServiceException
     */
    suspend fun createAlias(input: CreateAliasRequest): AliasConfiguration {
        // FIXME - for operation inputs/outputs should we wrap them as e.g. "CreateAliasResponse" even though this operation output was listed as "AliasConfiguration"
        return client.roundTrip(CreateAliasRequestSerializer(input), AliasConfigurationDeserializer())
    }

    suspend fun createAlias(block: CreateAliasRequest.DslBuilder.() -> Unit): AliasConfiguration {
        val input = CreateAliasRequest{ block(this) }
        return createAlias(input)
    }

    fun close() {
        client.close()
    }
}


fun main() = runBlocking{
    val client = LambdaClient()
    val request = InvokeRequest {
        functionName = "myfunction"
        payload = "some payload".toByteArray()
    }

    println("running 'invoke' operation")
    val resp = client.invoke(request)
    println(resp)

    println("running 'createAlias' operation")
    val aliasConfig = client.createAlias {
        name = "LIVE"
        functionName = "my-function"
        functionVersion = "1"
        description = "alias for LIVE"
    }
    println(aliasConfig)

    // println("running invalid 'createAlias' operation")
    // client.createAlias {
    //     name = "DEV"
    //     description = "alias for DEV"
    //     // missing version
    // }
    // println(aliasConfig)

    // FIXME - why isn't this exiting...seems like OkHTTP engine dispatcher isn't closing?
    client.close()
}