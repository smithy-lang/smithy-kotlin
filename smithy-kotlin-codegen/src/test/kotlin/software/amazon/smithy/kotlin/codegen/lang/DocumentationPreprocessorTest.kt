/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.DocumentationTrait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentationPreprocessorTest {
    @Test
    fun itEscapesSquareBrackets() {
        // https://github.com/awslabs/aws-sdk-kotlin/issues/153
        val model = """
        namespace com.test
        
        service FooService {
            version: "1.0.0"
        }
        
        @documentation("This should not be modified")
        structure Foo {
            @documentation("member docs")
            Unit: Unit,
            Str: String
        }
        
        @documentation("UserName@[SubDomain.]Domain.TopLevelDomain")
        structure Unit {
            Int: Integer
        }
        
        union MyUnion {
            @documentation("foo [bar [baz] qux] quux")
            Integer: Integer,
            String: String,
            Unit: Unit
        }
        """.toSmithyModel()

        val settings = KotlinSettings(
            ShapeId.from("com.test#FooService"),
            KotlinSettings.PackageSettings(
                "test",
                "1.0",
                ""
            ),
            "Foo"
        )

        val integration = DocumentationPreprocessor()
        val modified = integration.preprocessModel(model, settings)
        val expectedDocs = listOf(
            "com.test#Foo" to "This should not be modified",
            "com.test#Foo\$Unit" to "member docs",
            "com.test#Unit" to "UserName@&#91;SubDomain.&#93;Domain.TopLevelDomain",
            "com.test#MyUnion\$Integer" to "foo &#91;bar &#91;baz&#93; qux&#93; quux",
        )
        expectedDocs.forEach { (shapeId, expected) ->
            val shape = modified.expectShape(ShapeId.from(shapeId))
            val docs = shape.expectTrait<DocumentationTrait>().value
            assertEquals(expected, docs)
        }
    }

    @Test
    fun `it renders paragraphs`() {
        val input = "<p>this is paragraph</p><div>this too is paragraph</div>"
        val expected = """
        this is paragraph
        
        this too is paragraph
        """.trimIndent()
        inputTest(input, expected)
    }

    @Test
    fun `it renders lists`() {
        val input = "<p>the listed items are as follows:</p><ul><li>item 1</li><li><p>item 2<p><ol><li>subitem 1</li></ol></li></ul>"
        val expected = """
        the listed items are as follows:
        + item 1
        + item 2
           + subitem 1
        """.trimIndent()
        inputTest(input, expected)
    }

    @Test
    fun `it renders basic formatters`() {
        val input = "<strong>bold text</strong><br/><em>italic text</em>"
        val expected = """
        **bold text**
        *italic text*
        """.trimIndent()
        inputTest(input, expected)
    }

    @Test
    fun `it renders code block as-is`() {
        val input = "<code>handleXml(\"<xml><elem1/><elem2>child</elem2></xml>\");</code>"
        val expected = "`handleXml(\"<xml><elem1/><elem2>child</elem2></xml>\");`"
        inputTest(input, expected)
    }

    @Test
    fun `it throws on nested unescaped code blocks`() {
        val input = "<code>handleXml(\"<xml> <code></code> </xml>\");</code>"
        assertFailsWith<CodegenException> {
            inputTest(input, "")
        }
    }

    @Test
    fun `it handles anchors with and without href`() {
        val input = "<p>for more information see <a href=\"link.com\">this link</a></p><p>also reference <a>NoRefAnchor</a>"
        val expected = """
        for more information see [this link](link.com)
        
        also reference NoRefAnchor
        """.trimIndent()
        inputTest(input, expected)
    }

    @Test
    fun `it renders headers`() {
        val input = "<fullname>FooService</fullname><p>a service that is itself</p><dt>section 1</dt><dd>section 1 details</dd>"
        val expected = """
        # FooService
        a service that is itself
        
        ## section 1
        section 1 details
        """.trimIndent()
        inputTest(input, expected)
    }

    @Test
    fun `it renders nested structures`() {
        val input = "<fullname>FooService</fullname>" +
            "<p>a service that is itself</p>" +
            "<p>methods are as follows:</p>" +
            "<ul>" +
            "<li><strong>IMPORTANT</strong> do not use: <a href=\"https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateBucket.html\">CreateBucket</a></li>" +
            "</ul>"
        val expected = """
        # FooService
        a service that is itself
        
        methods are as follows:
        + **IMPORTANT** do not use: [CreateBucket](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateBucket.html)
        """.trimIndent()
        inputTest(input, expected)
    }

    @Test
    fun `it fully renders S3 CreateMultipartUpload`() {
        val input = """
<p>This action initiates a multipart upload and returns an upload ID. This upload ID is
used to associate all of the parts in the specific multipart upload. You specify this
upload ID in each of your subsequent upload part requests (see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html">UploadPart</a>). You also include this
upload ID in the final request to either complete or abort the multipart upload
request.</p>

<p>For more information about multipart uploads, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuoverview.html">Multipart Upload Overview</a>.</p>

<p>If you have configured a lifecycle rule to abort incomplete multipart uploads, the
upload must complete within the number of days specified in the bucket lifecycle
configuration. Otherwise, the incomplete multipart upload becomes eligible for an abort
action and Amazon S3 aborts the multipart upload. For more information, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuoverview.html#mpu-abort-incomplete-mpu-lifecycle-config">Aborting
Incomplete Multipart Uploads Using a Bucket Lifecycle Policy</a>.</p>

<p>For information about the permissions required to use the multipart upload API, see
<a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuAndPermissions.html">Multipart Upload and
Permissions</a>.</p>

<p>For request signing, multipart upload is just a series of regular requests. You initiate
a multipart upload, send one or more requests to upload parts, and then complete the
multipart upload process. You sign each request individually. There is nothing special
about signing multipart upload requests. For more information about signing, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html">Authenticating
Requests (Amazon Web Services Signature Version 4)</a>.</p>

<note>
<p> After you initiate a multipart upload and upload one or more parts, to stop being
charged for storing the uploaded parts, you must either complete or abort the multipart
upload. Amazon S3 frees up the space used to store the parts and stop charging you for
storing them only after you either complete or abort a multipart upload. </p>
</note>

<p>You can optionally request server-side encryption. For server-side encryption, Amazon S3
encrypts your data as it writes it to disks in its data centers and decrypts it when you
access it. You can provide your own encryption key, or use Amazon Web Services KMS keys or Amazon S3-managed encryption keys. If you choose to provide
your own encryption key, the request headers you provide in <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html">UploadPart</a> and <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPartCopy.html">UploadPartCopy</a> requests must match the headers you used in the request to
initiate the upload by using <code>CreateMultipartUpload</code>. </p>
<p>To perform a multipart upload with encryption using an Amazon Web Services KMS key, the requester must
have permission to the <code>kms:Decrypt</code> and <code>kms:GenerateDataKey*</code>
actions on the key. These permissions are required because Amazon S3 must decrypt and read data
from the encrypted file parts before it completes the multipart upload. For more
information, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html#mpuAndPermissions">Multipart upload API
and permissions</a> in the <i>Amazon S3 User Guide</i>.</p>

<p>If your Identity and Access Management (IAM) user or role is in the same Amazon Web Services account
as the KMS key, then you must have these permissions on the key policy. If your IAM
user or role belongs to a different account than the key, then you must have the
permissions on both the key policy and your IAM user or role.</p>

<p> For more information, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/serv-side-encryption.html">Protecting
Data Using Server-Side Encryption</a>.</p>

<dl>
<dt>Access Permissions</dt>
<dd>
<p>When copying an object, you can optionally specify the accounts or groups that
should be granted specific permissions on the new object. There are two ways to
grant the permissions using the request headers:</p>
<ul>
<li>
<p>Specify a canned ACL with the <code>x-amz-acl</code> request header. For
more information, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#CannedACL">Canned ACL</a>.</p>
</li>
<li>
<p>Specify access permissions explicitly with the
<code>x-amz-grant-read</code>, <code>x-amz-grant-read-acp</code>,
<code>x-amz-grant-write-acp</code>, and
<code>x-amz-grant-full-control</code> headers. These parameters map to
the set of permissions that Amazon S3 supports in an ACL. For more information,
see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html">Access Control List (ACL)
Overview</a>.</p>
</li>
</ul>
<p>You can use either a canned ACL or specify access permissions explicitly. You
cannot do both.</p>
</dd>
<dt>Server-Side- Encryption-Specific Request Headers</dt>
<dd>
<p>You can optionally tell Amazon S3 to encrypt data at rest using server-side
encryption. Server-side encryption is for data encryption at rest. Amazon S3 encrypts
your data as it writes it to disks in its data centers and decrypts it when you
access it. The option you use depends on whether you want to use Amazon Web Services managed
encryption keys or provide your own encryption key. </p>
<ul>
<li>
<p>Use encryption keys managed by Amazon S3 or customer managed key stored
in Amazon Web Services Key Management Service (Amazon Web Services KMS) – If you want Amazon Web Services to manage the keys
used to encrypt data, specify the following headers in the request.</p>
<ul>
<li>
<p>
<code>x-amz-server-side-encryption</code>
</p>
</li>
<li>
<p>
<code>x-amz-server-side-encryption-aws-kms-key-id</code>
</p>
</li>
<li>
<p>
<code>x-amz-server-side-encryption-context</code>
</p>
</li>
</ul>
<note>
<p>If you specify <code>x-amz-server-side-encryption:aws:kms</code>, but
don't provide <code>x-amz-server-side-encryption-aws-kms-key-id</code>,
Amazon S3 uses the Amazon Web Services managed key in Amazon Web Services KMS to protect the data.</p>
</note>
<important>
<p>All GET and PUT requests for an object protected by Amazon Web Services KMS fail if
you don't make them with SSL or by using SigV4.</p>
</important>
<p>For more information about server-side encryption with KMS key (SSE-KMS),
see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingKMSEncryption.html">Protecting Data Using Server-Side Encryption with KMS keys</a>.</p>
</li>
<li>
<p>Use customer-provided encryption keys – If you want to manage your own
encryption keys, provide all the following headers in the request.</p>
<ul>
<li>
<p>
<code>x-amz-server-side-encryption-customer-algorithm</code>
</p>
</li>
<li>
<p>
<code>x-amz-server-side-encryption-customer-key</code>
</p>
</li>
<li>
<p>
<code>x-amz-server-side-encryption-customer-key-MD5</code>
</p>
</li>
</ul>
<p>For more information about server-side encryption with KMS keys (SSE-KMS),
see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingKMSEncryption.html">Protecting Data Using Server-Side Encryption with KMS keys</a>.</p>
</li>
</ul>
</dd>
<dt>Access-Control-List (ACL)-Specific Request Headers</dt>
<dd>
<p>You also can use the following access control–related headers with this
operation. By default, all objects are private. Only the owner has full access
control. When adding a new object, you can grant permissions to individual Amazon Web Services accounts or to predefined groups defined by Amazon S3. These permissions are then added
to the access control list (ACL) on the object. For more information, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/S3_ACLs_UsingACLs.html">Using ACLs</a>. With this
operation, you can grant access permissions using one of the following two
methods:</p>
<ul>
<li>
<p>Specify a canned ACL (<code>x-amz-acl</code>) — Amazon S3 supports a set of
predefined ACLs, known as <i>canned ACLs</i>. Each canned ACL
has a predefined set of grantees and permissions. For more information, see
<a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#CannedACL">Canned
ACL</a>.</p>
</li>
<li>
<p>Specify access permissions explicitly — To explicitly grant access
permissions to specific Amazon Web Services accounts or groups, use the following headers.
Each header maps to specific permissions that Amazon S3 supports in an ACL. For
more information, see <a href="https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html">Access
Control List (ACL) Overview</a>. In the header, you specify a list of
grantees who get the specific permission. To grant permissions explicitly,
use:</p>
<ul>
<li>
<p>
<code>x-amz-grant-read</code>
</p>
</li>
<li>
<p>
<code>x-amz-grant-write</code>
</p>
</li>
<li>
<p>
<code>x-amz-grant-read-acp</code>
</p>
</li>
<li>
<p>
<code>x-amz-grant-write-acp</code>
</p>
</li>
<li>
<p>
<code>x-amz-grant-full-control</code>
</p>
</li>
</ul>
<p>You specify each grantee as a type=value pair, where the type is one of
the following:</p>
<ul>
<li>
<p>
<code>id</code> – if the value specified is the canonical user ID
of an Amazon Web Services account</p>
</li>
<li>
<p>
<code>uri</code> – if you are granting permissions to a predefined
group</p>
</li>
<li>
<p>
<code>emailAddress</code> – if the value specified is the email
address of an Amazon Web Services account</p>
<note>
<p>Using email addresses to specify a grantee is only supported in the following Amazon Web Services Regions: </p>
<ul>
<li>
<p>US East (N. Virginia)</p>
</li>
<li>
<p>US West (N. California)</p>
</li>
<li>
<p> US West (Oregon)</p>
</li>
<li>
<p> Asia Pacific (Singapore)</p>
</li>
<li>
<p>Asia Pacific (Sydney)</p>
</li>
<li>
<p>Asia Pacific (Tokyo)</p>
</li>
<li>
<p>Europe (Ireland)</p>
</li>
<li>
<p>South America (São Paulo)</p>
</li>
</ul>
<p>For a list of all the Amazon S3 supported Regions and endpoints, see <a href="https://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region">Regions and Endpoints</a> in the Amazon Web Services General Reference.</p>
</note>
</li>
</ul>
<p>For example, the following <code>x-amz-grant-read</code> header grants the Amazon Web Services accounts identified by account IDs permissions to read object data and its metadata:</p>
<p>
<code>x-amz-grant-read: id="11112222333", id="444455556666" </code>
</p>
</li>
</ul>

</dd>
</dl>

<p>The following operations are related to <code>CreateMultipartUpload</code>:</p>
<ul>
<li>
<p>
<a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html">UploadPart</a>
</p>
</li>
<li>
<p>
<a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_CompleteMultipartUpload.html">CompleteMultipartUpload</a>
</p>
</li>
<li>
<p>
<a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html">AbortMultipartUpload</a>
</p>
</li>
<li>
<p>
<a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListParts.html">ListParts</a>
</p>
</li>
<li>
<p>
<a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListMultipartUploads.html">ListMultipartUploads</a>
</p>
</li>
</ul>
"""
        val expected = """This action initiates a multipart upload and returns an upload ID. This upload ID is used to associate all of the parts in the specific multipart upload. You specify this upload ID in each of your subsequent upload part requests (see [UploadPart](https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html)). You also include this upload ID in the final request to either complete or abort the multipart upload request.

For more information about multipart uploads, see [Multipart Upload Overview](https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuoverview.html).

If you have configured a lifecycle rule to abort incomplete multipart uploads, the upload must complete within the number of days specified in the bucket lifecycle configuration. Otherwise, the incomplete multipart upload becomes eligible for an abort action and Amazon S3 aborts the multipart upload. For more information, see [Aborting Incomplete Multipart Uploads Using a Bucket Lifecycle Policy](https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuoverview.html#mpu-abort-incomplete-mpu-lifecycle-config).

For information about the permissions required to use the multipart upload API, see [Multipart Upload and Permissions](https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuAndPermissions.html).

For request signing, multipart upload is just a series of regular requests. You initiate a multipart upload, send one or more requests to upload parts, and then complete the multipart upload process. You sign each request individually. There is nothing special about signing multipart upload requests. For more information about signing, see [Authenticating Requests (Amazon Web Services Signature Version 4)](https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html).

 After you initiate a multipart upload and upload one or more parts, to stop being charged for storing the uploaded parts, you must either complete or abort the multipart upload. Amazon S3 frees up the space used to store the parts and stop charging you for storing them only after you either complete or abort a multipart upload. 

You can optionally request server-side encryption. For server-side encryption, Amazon S3 encrypts your data as it writes it to disks in its data centers and decrypts it when you access it. You can provide your own encryption key, or use Amazon Web Services KMS keys or Amazon S3-managed encryption keys. If you choose to provide your own encryption key, the request headers you provide in [UploadPart](https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html) and [UploadPartCopy](https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPartCopy.html) requests must match the headers you used in the request to initiate the upload by using `CreateMultipartUpload`. 

To perform a multipart upload with encryption using an Amazon Web Services KMS key, the requester must have permission to the `kms:Decrypt` and `kms:GenerateDataKey*` actions on the key. These permissions are required because Amazon S3 must decrypt and read data from the encrypted file parts before it completes the multipart upload. For more information, see [Multipart upload API and permissions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html#mpuAndPermissions) in the *Amazon S3 User Guide*.

If your Identity and Access Management (IAM) user or role is in the same Amazon Web Services account as the KMS key, then you must have these permissions on the key policy. If your IAM user or role belongs to a different account than the key, then you must have the permissions on both the key policy and your IAM user or role.

 For more information, see [Protecting Data Using Server-Side Encryption](https://docs.aws.amazon.com/AmazonS3/latest/dev/serv-side-encryption.html).

## Access Permissions
When copying an object, you can optionally specify the accounts or groups that should be granted specific permissions on the new object. There are two ways to grant the permissions using the request headers:
+ Specify a canned ACL with the `x-amz-acl` request header. For more information, see [Canned ACL](https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#CannedACL).
+ Specify access permissions explicitly with the `x-amz-grant-read`, `x-amz-grant-read-acp`, `x-amz-grant-write-acp`, and `x-amz-grant-full-control` headers. These parameters map to the set of permissions that Amazon S3 supports in an ACL. For more information, see [Access Control List (ACL) Overview](https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html).
You can use either a canned ACL or specify access permissions explicitly. You cannot do both.

## Server-Side- Encryption-Specific Request Headers
You can optionally tell Amazon S3 to encrypt data at rest using server-side encryption. Server-side encryption is for data encryption at rest. Amazon S3 encrypts your data as it writes it to disks in its data centers and decrypts it when you access it. The option you use depends on whether you want to use Amazon Web Services managed encryption keys or provide your own encryption key. 
+ Use encryption keys managed by Amazon S3 or customer managed key stored in Amazon Web Services Key Management Service (Amazon Web Services KMS) – If you want Amazon Web Services to manage the keys used to encrypt data, specify the following headers in the request.
   + `x-amz-server-side-encryption`
   + `x-amz-server-side-encryption-aws-kms-key-id`
   + `x-amz-server-side-encryption-context`
If you specify `x-amz-server-side-encryption:aws:kms`, but don't provide `x-amz-server-side-encryption-aws-kms-key-id`, Amazon S3 uses the Amazon Web Services managed key in Amazon Web Services KMS to protect the data.All GET and PUT requests for an object protected by Amazon Web Services KMS fail if you don't make them with SSL or by using SigV4.For more information about server-side encryption with KMS key (SSE-KMS), see [Protecting Data Using Server-Side Encryption with KMS keys](https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingKMSEncryption.html).
+ Use customer-provided encryption keys – If you want to manage your own encryption keys, provide all the following headers in the request.
   + `x-amz-server-side-encryption-customer-algorithm`
   + `x-amz-server-side-encryption-customer-key`
   + `x-amz-server-side-encryption-customer-key-MD5`
For more information about server-side encryption with KMS keys (SSE-KMS), see [Protecting Data Using Server-Side Encryption with KMS keys](https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingKMSEncryption.html).

## Access-Control-List (ACL)-Specific Request Headers
You also can use the following access control–related headers with this operation. By default, all objects are private. Only the owner has full access control. When adding a new object, you can grant permissions to individual Amazon Web Services accounts or to predefined groups defined by Amazon S3. These permissions are then added to the access control list (ACL) on the object. For more information, see [Using ACLs](https://docs.aws.amazon.com/AmazonS3/latest/dev/S3_ACLs_UsingACLs.html). With this operation, you can grant access permissions using one of the following two methods:
+ Specify a canned ACL (`x-amz-acl`) — Amazon S3 supports a set of predefined ACLs, known as *canned ACLs*. Each canned ACL has a predefined set of grantees and permissions. For more information, see [Canned ACL](https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#CannedACL).
+ Specify access permissions explicitly — To explicitly grant access permissions to specific Amazon Web Services accounts or groups, use the following headers. Each header maps to specific permissions that Amazon S3 supports in an ACL. For more information, see [Access Control List (ACL) Overview](https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html). In the header, you specify a list of grantees who get the specific permission. To grant permissions explicitly, use:
   + `x-amz-grant-read`
   + `x-amz-grant-write`
   + `x-amz-grant-read-acp`
   + `x-amz-grant-write-acp`
   + `x-amz-grant-full-control`
You specify each grantee as a type=value pair, where the type is one of the following:
   + `id` – if the value specified is the canonical user ID of an Amazon Web Services account
   + `uri` – if you are granting permissions to a predefined group
   + `emailAddress` – if the value specified is the email address of an Amazon Web Services accountUsing email addresses to specify a grantee is only supported in the following Amazon Web Services Regions: 
      + US East (N. Virginia)
      + US West (N. California)
      +  US West (Oregon)
      +  Asia Pacific (Singapore)
      + Asia Pacific (Sydney)
      + Asia Pacific (Tokyo)
      + Europe (Ireland)
      + South America (São Paulo)
For a list of all the Amazon S3 supported Regions and endpoints, see [Regions and Endpoints](https://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region) in the Amazon Web Services General Reference.
For example, the following `x-amz-grant-read` header grants the Amazon Web Services accounts identified by account IDs permissions to read object data and its metadata:`x-amz-grant-read: id="11112222333", id="444455556666" `

The following operations are related to `CreateMultipartUpload`:
+ [UploadPart](https://docs.aws.amazon.com/AmazonS3/latest/API/API_UploadPart.html)
+ [CompleteMultipartUpload](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CompleteMultipartUpload.html)
+ [AbortMultipartUpload](https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html)
+ [ListParts](https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListParts.html)
+ [ListMultipartUploads](https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListMultipartUploads.html)"""
        inputTest(input, expected)
    }

    private fun inputTest(input: String, expected: String) {
        val sanitizedInput = input
            .replace("\n", " ")
            .replace("\"", "\\\"")

        val model = """
        namespace com.test
        
        @documentation("$sanitizedInput")
        service FooService {
            version: "1.0.0"
        }
        """.toSmithyModel()

        val settings = KotlinSettings(
            ShapeId.from("com.test#FooService"),
            KotlinSettings.PackageSettings(
                "test",
                "1.0",
                ""
            ),
            "Foo"
        )

        val integration = DocumentationPreprocessor()
        val modified = integration.preprocessModel(model, settings)

        val shape = modified.expectShape(ShapeId.from("com.test#FooService"))
        val docs = shape.expectTrait<DocumentationTrait>().value
        assertEquals(expected, docs)
    }
}
