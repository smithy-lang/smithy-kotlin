/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.aws.ktlint.rules

import com.pinterest.ktlint.core.Rule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiCommentImpl
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.stubs.elements.KtFileElementType

class CopyrightHeaderRule : Rule("copyright-header") {
    companion object {
        private val header = """
        /*
         * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
         * SPDX-License-Identifier: Apache-2.0
         */
        
        """.trimIndent()
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        // Only applies to file nodes
        if (node.elementType != KtFileElementType.INSTANCE) {
            return
        }

        if (!node.text.startsWith(header.trim())) {
            emit(node.startOffset, "Missing or incorrect file header", true)

            if (autoCorrect) {
                val copyrightNode = PsiCommentImpl(KtTokens.BLOCK_COMMENT, header)
                node.addChild(copyrightNode, node.firstChildNode)
            }
        }
    }
}
