package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.kotlin.codegen.core.withBlock

internal fun KtorStubGenerator.writePerOperationHandlers() {
    operations.forEach { shape ->
        val inputShape = ctx.model.expectShape(shape.input.get())
        val inputSymbol = ctx.symbolProvider.toSymbol(inputShape)

        val outputShape = ctx.model.expectShape(shape.output.get())
        val outputSymbol = ctx.symbolProvider.toSymbol(outputShape)

        val name = shape.id.name

        delegator.useFileWriter("${name}Operation.kt", "$pkgName.operations") { writer ->

            writer.withBlock("public fun handle${name}Request(req: #T): #T {", "}", inputSymbol, outputSymbol) {
                write("// TODO: implement me")
                write("// To build a #T object:", outputSymbol)
                write("//   1. Use`#T.Builder()`", outputSymbol)
                write("//   2. Set fields like `#T.variable = ...`", outputSymbol)
                write("//   3. Return the built object using `return #T.build()`", outputSymbol)
                write("return #T.Builder().build()", outputSymbol)
            }
        }
    }
}
