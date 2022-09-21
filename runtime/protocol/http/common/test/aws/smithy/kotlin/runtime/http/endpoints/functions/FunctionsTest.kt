/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.endpoints.functions

import kotlin.test.*

class FunctionsTest {
    @Test
    fun testSubstringInvalidIndices() =
        assertNull(substring("foo", 2, 1, false))

    @Test
    fun testSubstringInvalidEndIndex() =
        assertNull(substring("foo", 0, 4, false))

    @Test
    fun testSubstring() =
        assertEquals("abc", substring("abcde", 0, 3, false))

    @Test
    fun testSubstringReversed() =
        assertEquals("cde", substring("abcde", 0, 3, true))

    @Test
    fun testParseUrlInvalidUrl() =
        assertNull(parseUrl("invalidscheme://"))

    @Test
    fun testParseUrlNoPort() =
        assertEquals(
            Url(
                "http",
                "hostname.com",
                "/path",
                "/path/",
                false,
            ),
            parseUrl("http://hostname.com/path"),
        )

    @Test
    fun testParseUrlStringAuthority() =
        assertEquals(
            Url(
                "http",
                "hostname.com:4001",
                "/path",
                "/path/",
                false,
            ),
            parseUrl("http://hostname.com:4001/path"),
        )

    @Test
    fun testParseUrlIpv4Authority() =
        assertEquals(
            Url(
                "http",
                "172.19.0.1:4001",
                "/path",
                "/path/",
                true,
            ),
            parseUrl("http://172.19.0.1:4001/path"),
        )

    @Test
    fun testParseUrlIpv6Authority() =
        assertEquals(
            Url(
                "http",
                "[::5949]:4001",
                "/path",
                "/path/",
                true,
            ),
            parseUrl("http://[::5949]:4001/path"),
        )

    @Test
    fun testIsVirtualHostableS3BucketOk() =
        assertTrue(
            isVirtualHostableS3Bucket("abc", false),
        )

    @Test
    fun testIsVirtualHostableS3BucketOkSubdomains() =
        assertTrue(
            isVirtualHostableS3Bucket("abc.def", true),
        )

    @Test
    fun testIsVirtualHostableS3BucketTooShort() =
        assertFalse(
            isVirtualHostableS3Bucket("cz", false),
        )

    @Test
    fun testIsVirtualHostableS3BucketTooLong() {
        val bucket = buildString {
            repeat(128) { append("i") }
        }
        assertFalse(
            isVirtualHostableS3Bucket(bucket, false),
        )
    }

    @Test
    fun testIsVirtualHostableS3BucketUppercase() =
        assertFalse(
            isVirtualHostableS3Bucket("CZZ", false),
        )

    @Test
    fun testIsVirtualHostableS3BucketIpv4() =
        assertFalse(
            isVirtualHostableS3Bucket("192.168.1.1", false),
        )

    @Test
    fun testIsVirtualHostableS3BucketIpv6() =
        assertFalse(
            isVirtualHostableS3Bucket("::1", false),
        )

    @Test
    fun testParseInvalidArn() =
        assertNull(parseArn("invalid"))

    @Test
    fun testParseInvalidLengthArn() =
        assertNull(parseArn("arn:aws"))

    @Test
    fun testParseArn() =
        assertEquals(
            Arn(
                "partition",
                "service",
                "region",
                "account-id",
                listOf("resource-type", "resource-id"),
            ),
            parseArn("arn:partition:service:region:account-id:resource-type/resource-id"),
        )

    private val testPartitions = listOf(
        Partition(
            id = "aws",
            regionRegex = Regex("^(us|eu|ap|sa|ca|me|af)-\\w+-\\d+$"),
            regions = mapOf(
                "us-east-1" to PartitionConfig(),
                "us-east-2" to PartitionConfig(),
                "us-west-1" to PartitionConfig(),
                "us-west-2" to PartitionConfig(),
                "aws-global" to PartitionConfig(
                    dnsSuffix = "override.amazonaws.com",
                ),
            ),
            baseConfig = PartitionConfig(
                name = "aws",
                dnsSuffix = "amazonaws.com",
                dualStackDnsSuffix = "api.aws",
                supportsFIPS = true,
                supportsDualStack = true,
            ),
        ),
        Partition(
            id = "aws-us-gov",
            regionRegex = Regex("^us\\-gov\\-\\w+\\-\\d+$"),
            regions = mapOf(
                "us-gov-west-1" to PartitionConfig(),
                "us-gov-east-1" to PartitionConfig(),
                "aws-us-gov-global" to PartitionConfig(),
            ),
            baseConfig = PartitionConfig(
                name = "aws-us-gov",
                dnsSuffix = "amazonaws.com",
                dualStackDnsSuffix = "api.aws",
                supportsFIPS = true,
                supportsDualStack = true,
            ),
        ),
    )

    @Test
    fun testPartitionExplicit() =
        assertEquals(
            testPartitions[0].baseConfig,
            actual = partition(testPartitions, "us-east-1"),
        )

    @Test
    fun testPartitionExplicitMerge() =
        assertEquals(
            PartitionConfig(
                name = "aws",
                dnsSuffix = "override.amazonaws.com",
                dualStackDnsSuffix = "api.aws",
                supportsFIPS = true,
                supportsDualStack = true,
            ),
            actual = partition(testPartitions, "aws-global"),
        )

    @Test
    fun testPartitionRegex() =
        assertEquals(
            testPartitions[1].baseConfig,
            actual = partition(testPartitions, "us-gov-arbitrary-5"),
        )

    @Test
    fun testPartitionFallback() =
        assertEquals(
            testPartitions[0].baseConfig,
            actual = partition(testPartitions, "foo"),
        )
}
