package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.aws.traits.auth.SigV4Trait
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes

internal fun KtorStubGenerator.writeAuthentication() {
    delegator.useFileWriter("UserPrincipal.kt", "$pkgName.auth") { writer ->
        writer.withBlock("public data class UserPrincipal(", ")") {
            write("val user: String")
        }
    }

    delegator.useFileWriter("Validation.kt", "$pkgName.auth") { writer ->

        writer.withBlock("internal object BearerValidation {", "}") {
            withBlock("public fun bearerValidation(token: String): UserPrincipal? {", "}") {
                write("// TODO: implement me")
                write("if (true) return UserPrincipal(#S) else return null", "Authenticated User")
            }
        }
    }

    delegator.useFileWriter("Authentication.kt", "$pkgName.auth") { writer ->
        writer.withBlock("internal fun #T.configureAuthentication() {", "}", RuntimeTypes.KtorServerCore.Application) {
            write("")
            withBlock(
                "#T(#T) {",
                "}",
                RuntimeTypes.KtorServerCore.install,
                RuntimeTypes.KtorServerAuth.Authentication,
            ) {
                withBlock("#T(#S) {", "}", RuntimeTypes.KtorServerAuth.bearer, "auth-bearer") {
                    write("realm = #S", "Access to API")
                    write("authenticate { cred -> BearerValidation.bearerValidation(cred.token) }")
                }
                withBlock("sigV4(name = #S) {", "}", "aws-sigv4") {
                    write("region = #T.region", ServiceTypes(pkgName).serviceFrameworkConfig)
                    val serviceSigV4AuthTrait = serviceShape.getTrait<SigV4Trait>()
                    if (serviceSigV4AuthTrait != null) {
                        write("service = #S", serviceSigV4AuthTrait.name)
                    }
                }
                withBlock("sigV4A(name = #S) {", "}", "aws-sigv4a") {
                    write("region = #T.region", ServiceTypes(pkgName).serviceFrameworkConfig)
                    val serviceSigV4AAuthTrait = serviceShape.getTrait<SigV4ATrait>()
                    if (serviceSigV4AAuthTrait != null) {
                        write("service = #S", serviceSigV4AAuthTrait.name)
                    }
                }
                write("provider(#S) { authenticate { ctx -> ctx.principal(Unit) } }", "no-auth")
            }
        }
    }
}
