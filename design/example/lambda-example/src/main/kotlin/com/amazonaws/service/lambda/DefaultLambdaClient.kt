package com.amazonaws.service.lambda

import com.amazonaws.service.runtime.*
import com.amazonaws.service.lambda.model.*
import com.amazonaws.service.lambda.transform.*
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.SdkBaseException
import software.aws.clientrt.ServiceException
import software.aws.clientrt.http.*
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.engine.ktor.KtorEngine

class DefaultLambdaClient: LambdaClient {
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
    override suspend fun invoke(input: InvokeRequest): InvokeResponse {
        return client.roundTrip(InvokeRequestSerializer(input), InvokeResponseDeserializer())
    }

    /**
     * @throws InvalidParameterValueException
     * @throws ClientException
     * @throws ServiceException
     */
    override suspend fun createAlias(input: CreateAliasRequest): AliasConfiguration {
        return client.roundTrip(CreateAliasRequestSerializer(input), AliasConfigurationDeserializer())
    }

    override fun close() {
        client.close()
    }
}


fun main() = runBlocking{
    val client = LambdaClient.create()
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

    println("running invalid 'createAlias' operation")
    try {
        client.createAlias {
            name = "DEV"
            description = "alias for DEV"
            // missing version
        }
    } catch (ex: SdkBaseException) {
        println("exception processing CreateAlias operation")
        println(ex)
    }

    // FIXME - why isn't this exiting...seems like OkHTTP engine dispatcher isn't closing?
    client.close()
}