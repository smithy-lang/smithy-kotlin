/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

import kotlin.text.set

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    val sdkVersion: String by project
    moduleVersion.set(sdkVersion)

    pluginsConfiguration.html {
        customStyleSheets.from(
            rootProject.file("docs/dokka-presets/css/aws-styles.css"),
        )

        customAssets.from(
            rootProject.file("docs/dokka-presets/assets/logo-icon.svg"),
            rootProject.file("docs/dokka-presets/scripts/accessibility.js"),
        )

        templatesDir.set(rootProject.file("docs/dokka-presets/templates"))

        footerMessage.set("© ${java.time.LocalDate.now().year}, Amazon Web Services, Inc. or its affiliates. All rights reserved.")
        separateInheritedMembers.set(true)
    }
}

dependencies {
    dokkaPlugin(project(":dokka-smithy"))
}
