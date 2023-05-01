/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.aws.ktlint.rules

import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

class MultilineIfElseBlockRule : Rule("multiline-if-else-block") {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (!node.isIfBlock() || node.startsWithBrace() || node.isOnParentLine() || node.isElseIfBlock()) {
            return
        }

        emit(node.firstChildNode.startOffset, "Missing braces around statement block", false)
    }

    /**
     * Determines if this node is a block in an `if` statement  (either "then" or "else").
     */
    private fun ASTNode.isIfBlock() = elementType == ElementType.THEN || elementType == ElementType.ELSE

    /**
     * Determines if this node is an `else if` block.
     */
    private fun ASTNode.isElseIfBlock() =
        elementType == ElementType.ELSE &&
            firstChildNode?.elementType == ElementType.IF

    /**
     * Determines if this node is on the same source file line number as its parent.
     */
    private fun ASTNode.isOnParentLine() = lineNumber() == treeParent?.lineNumber()

    private fun ASTNode.lineNumber() = psi.containingFile?.viewProvider?.document?.getLineNumber(this.startOffset)

    /**
     * Determines if this node starts with a left brace (i.e., `{`).
     */
    private fun ASTNode.startsWithBrace() = firstChildNode?.firstChildNode?.elementType == ElementType.LBRACE
}
