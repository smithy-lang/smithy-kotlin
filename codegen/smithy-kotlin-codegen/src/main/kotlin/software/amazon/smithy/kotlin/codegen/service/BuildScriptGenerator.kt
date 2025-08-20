package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.FileManifest

internal fun buildScript(fileManifest: FileManifest) {
    val bashScript = """
        #!/bin/bash
        
        if [ -d "build" ]; then
            read -p "The 'build' directory already exists. Removing it will delete previous build artifacts. Continue? (y/n): " choice
            case "${'$'}choice" in
                y|Y )
                    gradle clean
                    echo "Previous build directory removed."
                    ;;
                * )
                    echo "Aborted."
                    exit 1
                    ;;
            esac
        fi

        gradle build

    """.trimIndent()
    fileManifest.writeFile("build.sh", bashScript)

    val batchScript = """
        @echo off
        if exist build (
            set /p choice="The 'build' directory already exists. Removing it will delete previous build artifacts. Continue? (y/n): "
            if /i "%choice%"=="y" (
                gradle clean
                echo Previous build directory removed.
            ) else (
                echo Aborted.
                exit /b 1
            )
        )

        gradle build

    """.trimIndent()

    fileManifest.writeFile("build.bat", batchScript)
}
