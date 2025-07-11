/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.kotlin.codegen.model.buildSymbol

class ServiceTypes (val pkgName: String) {
    val LogLevel = buildSymbol {
        name = "LogLevel"
        namespace = "${pkgName}.config"
    }

    val ServiceEngine = buildSymbol {
        name = "ServiceEngine"
        namespace = "${pkgName}.config"
    }

    val ServiceFrameworkConfig = buildSymbol {
        name = "ServiceFrameworkConfig"
        namespace = "${pkgName}.config"
    }

    val KTORServiceFramework = buildSymbol {
        name = "KTORServiceFramework"
        namespace = "${pkgName}.framework"
    }

    val ErrorEnvelope = buildSymbol {
        name = "ErrorEnvelope"
        namespace = "${pkgName}.plugins"
    }

    val configureErrorHandling = buildSymbol {
        name = "configureErrorHandling"
        namespace = "${pkgName}.plugins"
    }

    val configureRouting = buildSymbol {
        name = "configureRouting"
        namespace = pkgName
    }

    val configureLogging = buildSymbol {
        name = "configureLogging"
        namespace = "${pkgName}.utils"
    }

    val ContentTypeGuard = buildSymbol {
        name = "ContentTypeGuard"
        namespace = "${pkgName}.plugins"
    }

}



