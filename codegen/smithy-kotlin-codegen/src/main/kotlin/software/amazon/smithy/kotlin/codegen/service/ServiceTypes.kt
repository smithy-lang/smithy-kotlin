/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.model.buildSymbol

class ServiceTypes(val pkgName: String) {
    val logLevel = buildSymbol {
        name = "LogLevel"
        namespace = "$pkgName.config"
    }

    val serviceEngine = buildSymbol {
        name = "ServiceEngine"
        namespace = "$pkgName.config"
    }

    val serviceFrameworkConfig = buildSymbol {
        name = "ServiceFrameworkConfig"
        namespace = "$pkgName.config"
    }

    val ktorServiceFramework = buildSymbol {
        name = "KTORServiceFramework"
        namespace = "$pkgName.framework"
    }

    val errorEnvelope = buildSymbol {
        name = "ErrorEnvelope"
        namespace = "$pkgName.plugins"
    }

    val configureErrorHandling = buildSymbol {
        name = "configureErrorHandling"
        namespace = "$pkgName.plugins"
    }

    val configureRouting = buildSymbol {
        name = "configureRouting"
        namespace = pkgName
    }

    val configureLogging = buildSymbol {
        name = "configureLogging"
        namespace = "$pkgName.utils"
    }

    val contentTypeGuard = buildSymbol {
        name = "ContentTypeGuard"
        namespace = "$pkgName.plugins"
    }
}
