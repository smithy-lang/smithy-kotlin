/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.smithy.test

import software.aws.clientrt.http.HttpBody
import software.aws.clientrt.http.readAll
import software.aws.clientrt.serde.xml.dom.XmlNode
import software.aws.clientrt.serde.xml.dom.toXmlString
import kotlin.test.assertEquals

/**
 * Assert JSON strings for equality ignoring key order
 */
suspend fun assertXmlStringsEqual(expected: String, actual: String) {
    // parse into a dom representation and sort the dom into a canonical form for comparison
    val expectedNode = XmlNode.parse(expected.encodeToByteArray()).apply { toCanonicalForm() }
    val actualNode = XmlNode.parse(expected.encodeToByteArray()).apply { toCanonicalForm() }

    val expectedCanonical = expectedNode.toXmlString(true)
    val actualCanonical = actualNode.toXmlString(true)
    assertEquals(expectedCanonical, actualCanonical, "expected XML:\n\n$expected\n\nactual:\n\n$actual\n")
}

/**
 * Assert HTTP bodies are equal as JSON documents
 */
suspend fun assertXmlBodiesEqual(expected: HttpBody?, actual: HttpBody?) {
    val expectedStr = expected?.readAll()?.decodeToString()
    val actualStr = actual?.readAll()?.decodeToString()
    if (expectedStr == null && actualStr == null) {
        return
    }

    requireNotNull(expectedStr) { "expected XML body cannot be null" }
    requireNotNull(actualStr) { "actual XML body cannot be null" }

    assertXmlStringsEqual(expectedStr, actualStr)
}

/**
 * Sort the XML dom node into a canonical representation
 */
fun XmlNode.toCanonicalForm() = toCanonical(this)

private fun toCanonical(root: XmlNode) {
    // attributes by name
    val sorted = root.attributes.toList().sortedBy { it.first.name }
    root.attributes.clear()
    root.attributes.putAll(sorted)

    // child nodes with concrete text value
    val nodesWithText = mutableListOf<XmlNode>()

    // child nodes with children
    val nodesWithChildren = mutableListOf<XmlNode>()

    root.children.values.forEach {
        it.forEach { node ->
            toCanonical(node)
            if (node.children.isNotEmpty()) {
                nodesWithChildren.add(node)
            } else {
                // including empty nodes
                nodesWithText.add(node)
            }
        }
    }

    // sort empty text nodes and nodes with no children by their name then by their textual value
    nodesWithText.sortWith(compareBy({ it.name.name }, { it.text }))

    nodesWithChildren.sortBy { it.name.name }

    // re-add all the sorted children
    root.children.clear()
    for (node in nodesWithText) {
        root.addChild(node)
    }
    for (node in nodesWithChildren) {
        root.addChild(node)
    }

    for (entry in root.children) {
        // only a single child node with this name, no more work to do
        if (entry.value.size <= 1) continue
        /*
            entry.value is a list of nodes like:

            <entry>
                <key>k1</key>
                <value>v1</value>
            </entry>

            <entry>
                <key>k1</key>
                <value>v1</value>
            </entry>

            ------- OR -------

            <member>m1</member>
            <member>m2</member>
            <member>m3</member>
         */

        // nodes with same name are only supported for either flattened lists or flattened maps
        // in which case we need to do a little more work to get the sort order correct
        val childCnt = entry.value[0].children.size
        check(entry.value.all { it.children.size == childCnt }) { "malformed xml, child count of ${entry.value[0]} nested list/map is unequal" }
        when (childCnt) {
            // flattened list with member
            0 -> entry.value.sortBy { it.text }
            // flattened list with a nested value
            1 -> entry.value.sortBy { it.text }
            // flattened map
            2 -> {
                /*
                 entry.value = listOf[A, A, ... A]
                 where A is like:
                     <A>
                          <key>k1</key>
                          <value>value1</value>
                      </A>
                 */

                // need to sort based on the nodes child keys
                entry.value.sortWith(compareFlatMapNodes())
            }
            else -> throw RuntimeException("malformed xml")
        }
    }
}

private fun compareFlatMapNodes(): Comparator<XmlNode> = compareBy {
    val children = it.children.toList().flatMap { it.second }
    check(children.size == 2) { "flatMap entry with more than 2 children; should only have a key/value pair" }
    // figure out which to compare by
    val idx = if (children[0].text != null && children[0].children.isEmpty()) {
        // likely key
        0
    } else {
        1
    }

    // sort entry.value by the children[idx].name.text (the map key node value)
    children[idx].text
}
