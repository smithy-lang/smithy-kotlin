package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.kotlin.codegen.model.toSymbol

/**
 * Commonly used external types.
 */
object ExternalTypes {
    // https://github.com/Kotlin/kotlinx.coroutines
    object KotlinxCoroutines {
        val Flow = "kotlinx.coroutines.flow.Flow".toSymbol()
        val FlowGenerator = "kotlinx.coroutines.flow.flow".toSymbol()
        val FlowTransform = "kotlinx.coroutines.flow.transform".toSymbol()
    }
}