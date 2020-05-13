/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KotlinWriterTest {

    @Test fun `writes doc strings`() {
        val writer = KotlinWriter("com.test")
        writer.dokka("These are the docs.\nMore.")
        val result = writer.toString()
        Assertions.assertTrue(result.contains("/**\n * These are the docs.\n * More.\n */\n"))
    }

    @Test fun `escapes $ in doc strings`() {
        val writer = KotlinWriter("com.test")
        val docs = "This is $ valid documentation."
        writer.dokka(docs)
        val result = writer.toString()
        Assertions.assertTrue(result.contains("/**\n * " + docs + "\n */\n"))
    }
}
