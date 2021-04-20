package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Test
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.traits.EnumDefinition
import kotlin.test.assertEquals

class NamingTest {

    @Test
    fun `it produces the correct string transformation for client names`() {
        // See https://awslabs.github.io/smithy/1.0/spec/aws/aws-core.html#using-sdk-service-id-for-client-naming
        assertEquals("ApiGateway", "API Gateway".clientName())
        assertEquals("Lambda", "Lambda".clientName())
        assertEquals("ElastiCache", "ElastiCache".clientName())
        assertEquals("ApiGatewayManagementApi", "ApiGatewayManagementApi".clientName())
        assertEquals("MigrationHubConfig", "MigrationHub Config".clientName())
        assertEquals("IoTFleetHub", "IoTFleetHub".clientName())
        assertEquals("Iot1ClickProjects", "IoT 1Click Projects".clientName())
        assertEquals("DynamoDb", "DynamoDB".clientName())
    }

    @Test
    fun testMemberNames() {
        val tests = listOf(
            "Foo" to "foo",
            "FooBar" to "fooBar",
            "ACL" to "acl",
            "ACLList" to "aclList",
            "fooey" to "fooey",
            "stringA" to "stringa",
            "StringA" to "stringa"
        )

        tests.forEach { (input, expected) ->
            val shape = MemberShape.builder()
                .id("com.test#TestStruct\$$input")
                .target("smithy.api#Integer")
                .build()
            assertEquals(expected, shape.defaultName(), "input: $input")
        }
    }

    @Test
    fun testEnumVariantNames() {
        // adapted from the V2 Java test suite

        val tests = listOf(
            "0" to "_0",
            "Twilio-Sms" to "TwilioSms",
            "t2.micro" to "T2Micro",
            "GreaterThanThreshold" to "GreaterThanThreshold",
            "INITIALIZED" to "Initialized",
            "GENERIC_EVENT" to "GenericEvent",
            "WINDOWS_2012" to "Windows2012",
            "ec2:spot-fleet-request:TargetCapacity" to "Ec2SpotFleetRequestTargetCapacity",
            "elasticmapreduce:instancegroup:InstanceCount" to "ElasticmapreduceInstancegroupInstanceCount",
            "application/vnd.amazonaws.card.generic" to "ApplicationVndAmazonawsCardGeneric",
            "IPV4" to "Ipv4",
            "ipv4" to "Ipv4",
            "IPv4" to "IpV4",
            "ipV4" to "IpV4",
            "IPMatch" to "IpMatch",
            "S3" to "S3",
            "EC2Instance" to "Ec2Instance",
            "aws.config" to "AwsConfig",
            "AWS::EC2::CustomerGateway" to "AwsEc2CustomerGateway",
            "application/pdf" to "ApplicationPdf",
            "ADConnector" to "AdConnector",
            "MS-CHAPv1" to "MsChapV1",
            "One-Way: Outgoing" to "OneWayOutgoing",
            "scram_sha_1" to "ScramSha1",
            "EC_prime256v1" to "EcPrime256V1",
            "EC_PRIME256V1" to "EcPrime256V1",
            "EC2v11.4" to "Ec2V11_4",
            "nodejs4.3-edge" to "Nodejs4_3_Edge",
            "BUILD_GENERAL1_SMALL" to "BuildGeneral1Small",
            "SSE_S3" to "SseS3",
            "http1.1" to "Http1_1",
            "T100" to "T100",
            "s3:ObjectCreated:*" to "S3ObjectCreated",
            "s3:ObjectCreated:Put" to "S3ObjectCreatedPut",
            "TLSv1" to "TlsV1",
            "TLSv1.2" to "TlsV1_2",
            "us-east-1" to "UsEast1",
            "io1" to "Io1",
            "testNESTEDAcronym" to "TestNestedAcronym",
            "IoT" to "Iot",
            "__foo___" to "Foo",
            "TEST__FOO" to "TestFoo",
            "IFrame" to "IFrame",
            "TPain" to "TPain",
            "S3EC2" to "S3Ec2",
            "S3Ec2" to "S3Ec2",
            "s3Ec2" to "S3Ec2",
            "s3ec2" to "S3Ec2",
            "i386" to "I386",
            "x86_64" to "X86_64",
            "arm64" to "Arm64",
        )

        tests.forEach { (input, expected) ->
            // NOTE: a lot of these are not valid names according to the Smithy spec but since
            // we still allow deriving a name from the enum value we want to verify what _would_ happen
            // should we encounter these inputs
            val definition = EnumDefinition.builder().name(input).value(input).build()
            val actual = definition.variantName()
            assertEquals(expected, actual, "input: $input")
        }
    }
}
