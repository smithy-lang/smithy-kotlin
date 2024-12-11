import kotlin.text.set

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    val sdkVersion: String by project
    moduleVersion.set(sdkVersion)
    moduleName.set("Smithy Kotlin")

    pluginsConfiguration.html {
        customStyleSheets.from(
            rootProject.file("docs/dokka-presets/css/logo-styles.css"),
            rootProject.file("docs/dokka-presets/css/aws-styles.css")
        )

        customAssets.from(
            rootProject.file("docs/dokka-presets/assets/logo-icon.svg"),
            rootProject.file("docs/dokka-presets/assets/aws_logo_white_59x35.png"),
            rootProject.file("docs/dokka-presets/scripts/accessibility.js")
        )

        footerMessage.set("© ${java.time.LocalDate.now().year}, Amazon Web Services, Inc. or its affiliates. All rights reserved.")
        separateInheritedMembers.set(true)
        templatesDir.set(rootProject.file("docs/dokka-presets/templates"))
    }

    dokkaPublications.html {
        includes.from(
            rootProject.file("docs/dokka-presets/README.md")
        )
    }

//    // Output subprojects' docs to <docs-base>/project-name/* instead of <docs-base>/path/to/project-name/*
//    // This is especially important for inter-repo linking (e.g., via externalDocumentationLink) because the
//    // package-list doesn't contain enough project path information to indicate where modules' documentation are
//    // located.
//    fileLayout.set { parent, child ->
//        parent.outputDirectory.dir(child.moduleName)
//    }
}

dependencies {
    dokkaPlugin(project(":dokka-smithy"))
}