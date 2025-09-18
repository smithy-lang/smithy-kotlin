/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.native.winver.*
import kotlinx.cinterop.*
import platform.posix.environ
import platform.posix.memcpy

public actual object SystemDefaultProvider : SystemDefaultProviderBase() {
    actual override val filePathSeparator: String = "\\"
    actual override fun osInfo(): OperatingSystem = OperatingSystem(OsFamily.Windows, osVersionFromKernel())

    actual override fun getAllEnvVars(): Map<String, String> = memScoped {
        generateSequence(0) { it + 1 }
            .map { idx -> environ.get(idx)?.toKString() }
            .takeWhile { it != null }
            .associate { env ->
                val parts = env?.split("=", limit = 2)
                check(parts?.size == 2) { "Environment entry \"$env\" is malformed" }
                parts[0] to parts[1]
            }
    }
}

// The functions below are adapted from C++ SDK:
// https://github.com/aws/aws-sdk-cpp/blob/0e6085bf0dd9a1cb1f27d101c4cf2db6ade6f307/src/aws-cpp-sdk-core/source/platform/windows/OSVersionInfo.cpp#L49-L106

private val wordHexFormat = HexFormat {
    upperCase = false
    number {
        removeLeadingZeros = true
        minLength = 4
    }
}

private data class LangCodePage(
    val language: UShort,
    val codePage: UShort,
)

private fun osVersionFromKernel(): String? = memScoped {
    withFileVersionInfo("Kernel32.dll") { versionInfoPtr ->
        getLangCodePage(versionInfoPtr)?.let { langCodePage ->
            getProductVersion(versionInfoPtr, langCodePage)
        }
    }
}

private inline fun <R> withFileVersionInfo(fileName: String, block: (CPointer<ByteVarOf<Byte>>) -> R?): R? {
    val blobSize = GetFileVersionInfoSizeW(fileName, null)
    val blob = ByteArray(blobSize.convert())
    blob.usePinned { pinned ->
        val result = GetFileVersionInfoW(fileName, 0u, blobSize, pinned.addressOf(0))
        return if (result == 0) {
            null
        } else {
            block(pinned.addressOf(0))
        }
    }
}

private fun MemScope.getLangCodePage(versionInfoPtr: CPointer<ByteVarOf<Byte>>): LangCodePage? {
    // Get _any_ language pack and codepage since they should all have the same version
    val langAndCodePagePtr = alloc<COpaquePointerVar>()
    val codePageSize = alloc<UIntVar>()
    val result = VerQueryValueW(
        versionInfoPtr,
        """\VarFileInfo\Translation""",
        langAndCodePagePtr.ptr,
        codePageSize.ptr,
    )

    return if (result == 0) {
        null
    } else {
        val langAndCodePage = langAndCodePagePtr.value!!.reinterpret<UIntVar>().pointed.value
        val language = (langAndCodePage and 0x0000ffffu).toUShort() // low WORD
        val codePage = (langAndCodePage and 0xffff0000u shr 16).toUShort() // high WORD
        LangCodePage(language, codePage)
    }
}

private fun MemScope.getProductVersion(versionInfoPtr: CPointer<ByteVarOf<Byte>>, langCodePage: LangCodePage): String? {
    val versionId = buildString {
        // Something like: \StringFileInfo\04090fb0\ProductVersion
        append("""\StringFileInfo\""")
        append(langCodePage.language.toHexString(wordHexFormat))
        append(langCodePage.codePage.toHexString(wordHexFormat))
        append("""\ProductVersion""")
    }

    // Get the block corresponding to versionId
    val block = alloc<COpaquePointerVar>()
    val blockSize = alloc<UIntVar>()
    val result = VerQueryValueW(versionInfoPtr, versionId, block.ptr, blockSize.ptr)

    return if (result == 0) {
        null
    } else {
        // Copy the bytes into a Kotlin byte array
        val blockBytes = ByteArray(blockSize.value.convert())
        blockBytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), block.value!!.reinterpret<ByteVar>(), blockSize.value.convert())
        }
        blockBytes.decodeToString()
    }
}
