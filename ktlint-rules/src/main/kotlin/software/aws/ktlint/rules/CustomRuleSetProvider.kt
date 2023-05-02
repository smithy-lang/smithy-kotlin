/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.aws.ktlint.rules

import com.pinterest.ktlint.core.RuleProvider
import com.pinterest.ktlint.core.RuleSetProviderV2

class CustomRuleSetProvider : RuleSetProviderV2("custom-ktlint-rules", NO_ABOUT) {
    override fun getRuleProviders() = setOf(
        RuleProvider { CopyrightHeaderRule() },
        RuleProvider { ExpressionBodyRule() },
        RuleProvider { MultilineIfElseBlockRule() },
    )
}
