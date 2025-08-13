package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.service.KtorStubGenerator

internal fun KtorStubGenerator.writePerOperationHandlers() {
    operations.forEach { shape ->
        val name = shape.id.name
        delegator.useFileWriter("${name}Operation.kt", "$pkgName.operations") { writer ->
            writer.addImport("$pkgName.model", "${shape.id.name}Request")
            writer.addImport("$pkgName.model", "${shape.id.name}Response")

            writer.withBlock("public fun handle${name}Request(req: ${name}Request): ${name}Response {", "}") {
                write("// TODO: implement me")
                write("// To build a ${name}Response object:")
                write("//   1. Use`${name}Response.Builder()`")
                write("//   2. Set fields like `${name}Response.variable = ...`")
                write("//   3. Return the built object using `return ${name}Response.build()`")
                write("return ${name}Response.Builder().build()")
            }
        }
    }
}
