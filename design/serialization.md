# Background

## Android SDK

The Android SDK code generator generates a top level service package that defines the service interface and a client that implements that interface. 

It also generates a subpackage `model` that contains all of the structure shapes. Underneath of the model package is another package called `transform` that implements serialization/deserialization for each of the corresponding model types.


Example from DynamoDB of the `createBackup()` service call. The client creates the request marshaller for the input type and marshalls (serializes) the input into a (HTTP) Request object.

It then sets up the handler that should be used for deserializing the response and calls the common function `invoke` with the request, response handler, and an execution context (which handles things like AWS credentials, user agent, metrics, signing, etc).


```java
// `aws-android-sdk-ddb: AmazonDynamoDBClient.java`

public CreateBackupResult createBackup(CreateBackupRequest createBackupRequest)
        throws AmazonServiceException, AmazonClientException {
    ExecutionContext executionContext = createExecutionContext(createBackupRequest);
    AWSRequestMetrics awsRequestMetrics = executionContext.getAwsRequestMetrics();
    awsRequestMetrics.startEvent(Field.ClientExecuteTime);
    Request<CreateBackupRequest> request = null;
    Response<CreateBackupResult> response = null;
    try {
        awsRequestMetrics.startEvent(Field.RequestMarshallTime);
        try {
            request = new CreateBackupRequestMarshaller().marshall(createBackupRequest);
            // Binds the request metrics to the current request.
            request.setAWSRequestMetrics(awsRequestMetrics);
        } finally {
            awsRequestMetrics.endEvent(Field.RequestMarshallTime);
        }
        Unmarshaller<CreateBackupResult, JsonUnmarshallerContext> unmarshaller = new CreateBackupResultJsonUnmarshaller();
        JsonResponseHandler<CreateBackupResult> responseHandler = new JsonResponseHandler<CreateBackupResult>(
                unmarshaller);

        response = invoke(request, responseHandler, executionContext);

        return response.getAwsResponse();
    } finally {
        awsRequestMetrics.endEvent(Field.ClientExecuteTime);
        endClientExecution(awsRequestMetrics, request, response, LOGGING_AWS_REQUEST_METRIC);
    }
}

```


The request serializer for a specific type directly handles both headers as well as JSON bodies and returns a fully formed request.

```java
// `aws-android-sdk-ddb: CreateBackupRequestMarshaller.java`

package com.amazonaws.services.dynamodbv2.model.transform;

import static com.amazonaws.util.StringUtils.UTF8;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.Request;
import com.amazonaws.DefaultRequest;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.transform.Marshaller;
import com.amazonaws.util.BinaryUtils;
import com.amazonaws.util.StringUtils;
import com.amazonaws.util.StringInputStream;
import com.amazonaws.util.json.AwsJsonWriter;
import com.amazonaws.util.json.JsonUtils;

/**
 * JSON request marshaller for CreateBackupRequest
 */
public class CreateBackupRequestMarshaller implements
        Marshaller<Request<CreateBackupRequest>, CreateBackupRequest> {

    public Request<CreateBackupRequest> marshall(CreateBackupRequest createBackupRequest) {
        if (createBackupRequest == null) {
            throw new AmazonClientException(
                    "Invalid argument passed to marshall(CreateBackupRequest)");
        }

        Request<CreateBackupRequest> request = new DefaultRequest<CreateBackupRequest>(
                createBackupRequest, "AmazonDynamoDB");
        String target = "DynamoDB_20120810.CreateBackup";
        request.addHeader("X-Amz-Target", target);
        request.setHttpMethod(HttpMethodName.POST);

        String uriResourcePath = "/";
        request.setResourcePath(uriResourcePath);
        try {
            StringWriter stringWriter = new StringWriter();
            AwsJsonWriter jsonWriter = JsonUtils.getJsonWriter(stringWriter);
            jsonWriter.beginObject();

            if (createBackupRequest.getTableName() != null) {
                String tableName = createBackupRequest.getTableName();
                jsonWriter.name("TableName");
                jsonWriter.value(tableName);
            }
            if (createBackupRequest.getBackupName() != null) {
                String backupName = createBackupRequest.getBackupName();
                jsonWriter.name("BackupName");
                jsonWriter.value(backupName);
            }

            jsonWriter.endObject();
            jsonWriter.close();
            String snippet = stringWriter.toString();
            byte[] content = snippet.getBytes(UTF8);
            request.setContent(new StringInputStream(snippet));
            request.addHeader("Content-Length", Integer.toString(content.length));
        } catch (Throwable t) {
            throw new AmazonClientException(
                    "Unable to marshall request to JSON: " + t.getMessage(), t);
        }
        if (!request.getHeaders().containsKey("Content-Type")) {
            request.addHeader("Content-Type", "application/x-amz-json-1.0");
        }

        return request;
    }
}

```

## Java SDK

The generated structure of a client in the AWS Java SDK is very similar but the implementation is drastically different. There is still a top level `service` directory as well as `model` and `model.transform` subpackages that all serve the same purpose as the ones generated in the Android SDK.

The following is the same client and request from above in the Android SDK.


```java
// `aws-java-sdk-dynamodb: AmazonDynamoDBClient.java`

    @SdkInternalApi
    final CreateBackupResult executeCreateBackup(CreateBackupRequest createBackupRequest) {

        ExecutionContext executionContext = createExecutionContext(createBackupRequest);
        AWSRequestMetrics awsRequestMetrics = executionContext.getAwsRequestMetrics();
        awsRequestMetrics.startEvent(Field.ClientExecuteTime);
        Request<CreateBackupRequest> request = null;
        Response<CreateBackupResult> response = null;

        try {
            awsRequestMetrics.startEvent(Field.RequestMarshallTime);
            try {
                request = new CreateBackupRequestProtocolMarshaller(protocolFactory).marshall(super.beforeMarshalling(createBackupRequest));
                // Binds the request metrics to the current request.
                request.setAWSRequestMetrics(awsRequestMetrics);
                request.addHandlerContext(HandlerContextKey.SIGNING_REGION, getSigningRegion());
                request.addHandlerContext(HandlerContextKey.SERVICE_ID, "DynamoDB");
                request.addHandlerContext(HandlerContextKey.OPERATION_NAME, "CreateBackup");
                request.addHandlerContext(HandlerContextKey.ADVANCED_CONFIG, advancedConfig);

            } finally {
                awsRequestMetrics.endEvent(Field.RequestMarshallTime);
            }

            URI cachedEndpoint = null;
            if (endpointDiscoveryEnabled) {
                cachedEndpoint = cache.get(awsCredentialsProvider.getCredentials().getAWSAccessKeyId(), false, endpoint);
            }

            HttpResponseHandler<AmazonWebServiceResponse<CreateBackupResult>> responseHandler = protocolFactory.createResponseHandler(
                    new JsonOperationMetadata().withPayloadJson(true).withHasStreamingSuccessResponse(false), new CreateBackupResultJsonUnmarshaller());
            response = invoke(request, responseHandler, executionContext, cachedEndpoint, null);

            return response.getAwsResponse();

        } finally {

            endClientExecution(awsRequestMetrics, request, response);
        }
    }
```



The client looks very similar, the largest difference is in the serialization (marshalling) logic. There are two classes involved a `ProtocolMarshaller` and a `Marshaller`.

The former sets up the marshalling for a particular protocol (in this case it's a JSON-Rest protocol). The protocol marshaller has generic hooks for how to marshall particular types into specific locations.

The latter uses the protocol marshaller to serialize (marshall) it's specific members into the correct locations.


See [JsonProtocolMarshaller](https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-core/src/main/java/com/amazonaws/protocol/json/internal/JsonProtocolMarshaller.java)

```java
// `aws-java-sdk-dynamodb: CreateBackupRequestProtocolMarshaller.java`

@SdkInternalApi
public class CreateBackupRequestProtocolMarshaller implements Marshaller<Request<CreateBackupRequest>, CreateBackupRequest> {

    private static final OperationInfo SDK_OPERATION_BINDING = OperationInfo.builder().protocol(Protocol.AWS_JSON).requestUri("/")
            .httpMethodName(HttpMethodName.POST).hasExplicitPayloadMember(false).hasPayloadMembers(true).operationIdentifier("DynamoDB_20120810.CreateBackup")
            .serviceName("AmazonDynamoDBv2").build();

    private final com.amazonaws.protocol.json.SdkJsonProtocolFactory protocolFactory;

    public CreateBackupRequestProtocolMarshaller(com.amazonaws.protocol.json.SdkJsonProtocolFactory protocolFactory) {
        this.protocolFactory = protocolFactory;
    }

    public Request<CreateBackupRequest> marshall(CreateBackupRequest createBackupRequest) {

        if (createBackupRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            final ProtocolRequestMarshaller<CreateBackupRequest> protocolMarshaller = protocolFactory.createProtocolMarshaller(SDK_OPERATION_BINDING,
                    createBackupRequest);

            protocolMarshaller.startMarshalling();
            CreateBackupRequestMarshaller.getInstance().marshall(createBackupRequest, protocolMarshaller);
            return protocolMarshaller.finishMarshalling();
        } catch (Exception e) {
            throw new SdkClientException("Unable to marshall request to JSON: " + e.getMessage(), e);
        }
    }

}

```

`CreateBackupRequestMarshaller` stores metadata about each field (via [MarshallingInfo](https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-core/src/main/java/com/amazonaws/protocol/MarshallingInfo.java) that generically ties that field to a particular location. The protocol marshaller is the one that understands what to do with that metadata. The protocol marshaller [marshall()](https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-core/src/main/java/com/amazonaws/protocol/json/internal/JsonProtocolMarshaller.java#L150) method is called with the actual value of each field and the corresponding static metadata about where that field should be serialized. 


```java
// `aws-java-sdk-dynamodb: CreateBackupRequestMarshaller.java`

public class CreateBackupRequestMarshaller {

    private static final MarshallingInfo<String> TABLENAME_BINDING = MarshallingInfo.builder(MarshallingType.STRING).marshallLocation(MarshallLocation.PAYLOAD)
            .marshallLocationName("TableName").build();
    private static final MarshallingInfo<String> BACKUPNAME_BINDING = MarshallingInfo.builder(MarshallingType.STRING)
            .marshallLocation(MarshallLocation.PAYLOAD).marshallLocationName("BackupName").build();

    private static final CreateBackupRequestMarshaller instance = new CreateBackupRequestMarshaller();

    public static CreateBackupRequestMarshaller getInstance() {
        return instance;
    }

    /**
     * Marshall the given parameter object.
     */
    public void marshall(CreateBackupRequest createBackupRequest, ProtocolMarshaller protocolMarshaller) {

        if (createBackupRequest == null) {
            throw new SdkClientException("Invalid argument passed to marshall(...)");
        }

        try {
            protocolMarshaller.marshall(createBackupRequest.getTableName(), TABLENAME_BINDING);
            protocolMarshaller.marshall(createBackupRequest.getBackupName(), BACKUPNAME_BINDING);
        } catch (Exception e) {
            throw new SdkClientException("Unable to marshall request to JSON: " + e.getMessage(), e);
        }
    }

}
```


# Client Runtime


There doesn't appear to be any (easy) way to provide a multiplatform serializer/deserializer for JSON

Both the Java SDK as well as the Android SDK provide a generic interface for writing primitives to JSON.

The interfaces inside kotlinx.serialization that would allow us to write something like [that](https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-core/src/main/java/com/amazonaws/protocol/json/SdkJsonGenerator.java) are not exposed publicly in kotlinx.serialization See [encoder/decoder](https://github.com/Kotlin/kotlinx.serialization/tree/master/runtime/commonMain/src/kotlinx/serialization/json/internal).

What that means is the client-runtime can do one of two things:

1. It can provide an interface for example to encode/decode JSON similar to what the Android and Java SDK's do e.g. `startArray()`, `writeInt(value: Int)`, etc. It would then have to provide the actual implementation for each platform by passing in the concrete implementation.

On JVM/Android side this would likely be Jackson/GSON.

I'm not sure if there is an equivalent on the iOS side.


2. The client-runtime does not provide any serialization interface. Instead it just provides the hook for the serialization stage.  This is the `middleware` layer that the Go/Typescript SDK's have defined.


Both of these solutions come with the fallout of having more than one library dependency.

```
FooService Android Client ---> client-runtime-core
                          |--> serialization-android (small wrapper type that implements interface using GSON)

FooService iOS Client     ---> client-runtime-core
                          |--> serialization-ios (small wrapper type that implements interface using XYZ as Apple Framework/Swift Package)


This then expands even further when we generate the AWS specific clients since there will be a separate client runtime that provides the AWS specific functionality clients will depend on (e.g. request signing).

FooService Android Client ---> client-runtime-core
                          |--> serialization-android (small wrapper type that implements interface using GSON)
                          |--> aws-client-rt

FooService iOS Client     ---> client-runtime-core
                          |--> serialization-ios (small wrapper type that implements interface using XYZ as Apple Framework/Swift Package)
                          |--> aws-client-rt
```

