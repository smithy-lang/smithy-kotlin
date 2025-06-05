package software.amazon.smithy.kotlin.protocolTests

import jdk.internal.net.http.common.Log.errors
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.kotlin.codegen.pt.KotlinProtocolTestCodegenPlugin
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode
import software.amazon.smithy.model.shapes.ServiceShape
import java.nio.file.Path

/**
 * Runs the `KotlinProtocolTestCodegenPlugin` directly. Leaves the results in the directory
 * that is given as an argument.
 */
fun generateProtocolTests(model: Model, service: ServiceShape, codegenPath: Path) {
    val context = PluginContext.builder()
        .model(model)
        .fileManifest(FileManifest.create(codegenPath))
        .settings(settingsForService(service))
        .build()
    val plugin = KotlinProtocolTestCodegenPlugin()
    plugin.execute(context)
}

/**
 * Creates the `Node` instance that represents the configuration used to run the
 * `KotlinProtocolTestCodegenPlugin` plugin.
 */
private fun settingsForService(service: ServiceShape): ObjectNode {
    return ObjectNode.builder()
        .withMember("service", service.id.toString())
        .withMember("package", packageSettings(service))
        .withMember("build", buildSettings())
        .withMember("api", apiSettings())
        .build()
}

/**
 * Crates the `Node` instance that defines the package settings.
 */
private fun packageSettings(service: ServiceShape): ObjectNode {
    val version = service.version ?: "1.0"
    return ObjectNode.builder()
        .withMember("version", version)
        .withMember("name", "smithy.protocolTests")
        .build()
}

/**
 * Creates the `Node` instance that defines the build settings.
 */
private fun buildSettings(): ObjectNode {
    return ObjectNode.builder()
        .withMember("generateFullProject", true)
        .withMember(
            "optInAnnotations",
            ArrayNode.arrayNode(StringNode.from("aws.smithy.kotlin.runtime.InternalApi")),
        )
        .withMember("rootProject", true)
        .build()
}

/**
 * Creates the `Node` instance that defines the api settings.
 */
private fun apiSettings(): ObjectNode {
    return ObjectNode.builder()
        // By default, this value is WHEN_DIFFERENT, which is incorrect for all protocols
        .withMember("defaultValueSerializationMode", "always")
        .build()
}
