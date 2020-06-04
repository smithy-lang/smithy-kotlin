package com.amazonaws.service.lambda

import com.amazonaws.service.runtime.*
import com.amazonaws.service.lambda.model.*
import com.amazonaws.service.lambda.transform.InvokeRequestSerializer
import com.amazonaws.service.lambda.transform.InvokeResponseDeserializer
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.http.*
import software.aws.clientrt.http.engine.HttpClientEngineConfig
import software.aws.clientrt.http.engine.ktor.KtorEngine
import software.aws.clientrt.http.request.HttpRequestPipeline

class LambdaClient: SdkClient {
    override val serviceName: String = "lambda"

    private val client: SdkHttpClient

    init {
        val config = HttpClientEngineConfig()
        client = sdkHttpClient(KtorEngine(config)) {
            install(HttpSerde) {
                serializer = JsonSerializer()
                deserializer = JsonDeserializer()
            }
        }
        // set the defaults - TODO this would likely be implemented as a "DefaultRequest" feature or similar
        client.requestPipeline.intercept(HttpRequestPipeline.Initialize) {
            context.url.scheme = Protocol.HTTP
            context.url.host = "127.0.0.1"
            context.url.port = 8000
        }
    }

    /**
     * @throws ServiceException
     * @throws ResourceNotFoundException
     * @throws InvalidRequestContentException
     * @throws RequestTooLargeException
     * @throws UnsupportedMediaTypeException
     * @throws TooManyRequestsException
     * @throws InvalidParameterValueException
     * @throws Ec2UnexpectedException
     * @throws SubnetIpAddressLimitReachedException
     * @throws EniLimitReachedException
     * @throws Ec2ThrottledException
     * @throws Ec2AccessDeniedException
     * @throws InvalidSubnetIdException
     * @throws InvalidSecurityGroupIdException
     * @throws InvalidZipFileException
     * @throws KmsDisabledException
     * @throws KmsInvalidStateException,
     * @throws KmsAccessDeniedException
     * @throws KmsNotFoundException
     * @throws InvalidRuntimeException
     * @throws ResourceConflictException
     * @throws ResourceNotReadyException
     * @throws AwsServiceException
     * @throws SdkClientException
     * @throws LambdaException
     */
    suspend fun invoke(input: InvokeRequest): InvokeResponse {
        return client.roundTrip(InvokeRequestSerializer(input), InvokeResponseDeserializer())
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

    val resp =  client.invoke(request)
    println(resp)

    // FIXME - why isn't this exiting...seems like OkHTTP engine dispatcher isn't closing?
    client.close()
}