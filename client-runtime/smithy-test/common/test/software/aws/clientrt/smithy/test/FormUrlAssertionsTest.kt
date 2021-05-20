/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.smithy.test

import kotlin.test.Test
import kotlin.test.assertFails

class FormUrlAssertionsTest {
    @Test
    fun testAssertFormUrlStringsEqual() {
        val x = """
         Action=TestAssertions
         &Version=2020-01-08
         &MapOfLists.entry.1.key=bar
         &MapOfLists.entry.1.value.member.1=C
         &MapOfLists.entry.1.value.member.2=D
         &MapOfLists.entry.2.key=foo
         &MapOfLists.entry.2.value.member.1=A
         &MapOfLists.entry.2.value.member.2=B
         &ListArg.member.1=foo
         &ListArg.member.2=bar
         &ListArg.member.3=baz
         &ComplexListArg.member.1.hi=hello
         &ComplexListArg.member.2.hi=hola
       """

        val y = """
         ListArg.member.1=foo
         &ListArg.member.2=bar
         &ListArg.member.3=baz
         &ComplexListArg.member.1.hi=hello
         &ComplexListArg.member.2.hi=hola
         &Action=TestAssertions
         &Version=2020-01-08
         &MapOfLists.entry.2.key=foo
         &MapOfLists.entry.2.value.member.1=A
         &MapOfLists.entry.2.value.member.2=B
         &MapOfLists.entry.1.key=bar
         &MapOfLists.entry.1.value.member.1=C
         &MapOfLists.entry.1.value.member.2=D
        """

        assertFormUrlStringsEqual(x, y)
    }

    @Test
    fun testAssertNotEqual() {
        val x = "Action=QueryMaps&Version=2020-01-08&MapArg.entry.1.key=foo&MapArg.entry.1.value=Foo&MapArg.entry.2.key=bar&MapArg.entry.2.value=Bar"
        val y = "Action=QueryMaps&Version=2020-01-08&MapArg.entry.1.key=foo&MapArg.entry.1.value=Foo"
        assertFails {
            assertFormUrlStringsEqual(x, y)
        }
    }
}
