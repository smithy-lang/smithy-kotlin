/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.test.prependNamespaceAndService
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ShapeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NamingTest {

    @Test
    fun `it produces the correct string transformation for client names`() {
        // See https://awslabs.github.io/smithy/1.0/spec/aws/aws-core.html#using-sdk-service-id-for-client-naming
        assertEquals("ApiGateway", clientName("API Gateway"))
        assertEquals("Lambda", clientName("Lambda"))
        assertEquals("ElastiCache", clientName("ElastiCache"))
        assertEquals("ApiGatewayManagement", clientName("ApiGatewayManagementApi"))
        assertEquals("MigrationHubConfig", clientName("MigrationHub Config"))
        assertEquals("IoTFleetHub", clientName("IoTFleetHub"))
        assertEquals("Iot1ClickProjects", clientName("IoT 1Click Projects"))
        assertEquals("DynamoDb", clientName("DynamoDB"))

        // sdkId sanitization rules
        assertEquals("Directory", clientName("Directory Service"))
        assertEquals("Foo", clientName("FooClient"))
        assertEquals("Foo", clientName("Fooservice"))
        assertEquals("Foo", clientName("Foo Service"))
        assertEquals("Foo", clientName("FooApI"))
        assertEquals("FooApiBar", clientName("FooApiBar"))
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
            "StringA" to "stringa",
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
            val actual = input.enumVariantName()
            assertEquals(expected, actual, "input: $input")
        }
    }

    @Test
    fun testUnionVariantNames() {
        val tests = listOf(
            "Foo" to "Foo",
            "FooBar" to "FooBar",
            "NULL" to "Null",
            "null" to "Null",
        )

        tests.forEach { (input, expected) ->

            val model = """
                structure TestStruct {
                    $input: Integer
                }
            """.prependNamespaceAndService().toSmithyModel()

            val shape = model.expectShape<MemberShape>("com.test#TestStruct\$$input")
            assertEquals(expected, shape.unionVariantName(), "input: $input")
        }
    }

    @Test
    fun testMangledNames() {
        val model = """
            structure Foo {
                member1: Integer,
                member2: Nested
            }
            
            structure Nested { }
        """.prependNamespaceAndService().toSmithyModel()

        val shape = model.expectShape(ShapeId.from("com.test#Foo"))

        val all = shape.mangledSuffix(shape.members())
        val none = shape.mangledSuffix()
        assertEquals("", all)
        assertEquals("", none)

        val firstMember = shape.mangledSuffix(shape.members().take(1))
        val secondMember = shape.mangledSuffix(shape.members().drop(1))

        assertNotEquals(all, firstMember)
        assertNotEquals(firstMember, secondMember)
    }
}
