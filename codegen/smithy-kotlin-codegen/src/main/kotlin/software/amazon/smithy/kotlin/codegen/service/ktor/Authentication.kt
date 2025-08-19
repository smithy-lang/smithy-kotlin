package software.amazon.smithy.kotlin.codegen.service.ktor

import software.amazon.smithy.aws.traits.auth.SigV4ATrait
import software.amazon.smithy.aws.traits.auth.SigV4Trait
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.service.ServiceTypes

/**
 * Writes Ktor-based authentication support classes and configuration
 * for a generated service.
 *
 * This generates three files:
 * 1. UserPrincipal.kt → Represents the authenticated user.
 * 2. Validation.kt → Provides bearer token validation logic.
 * 3. Authentication.kt → Configures authentication providers in Ktor.
 */
internal fun KtorStubGenerator.writeAuthentication() {
    delegator.useFileWriter("UserPrincipal.kt", "$pkgName.auth") { writer ->
        writer.withBlock("public data class UserPrincipal(", ")") {
            write("val user: String")
        }
    }

    delegator.useFileWriter("Validation.kt", "$pkgName.auth") { writer ->

        writer.withBlock("internal object BearerValidation {", "}") {
            withBlock("public fun bearerValidation(token: String): UserPrincipal? {", "}") {
                write("// TODO: implement me:")
                write("//  Validate the provided bearer token and return a UserPrincipal if valid.")
                write("//  Return a UserPrincipal with user information (e.g., user id, roles) if valid,")
                write("//  or return null if the token is invalid or expired.")
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
                    serviceShape.getTrait<SigV4Trait>()?.let {
                        write("service = #S", it.name)
                    }
                }
                withBlock("sigV4A(name = #S) {", "}", "aws-sigv4a") {
                    write("region = #T.region", ServiceTypes(pkgName).serviceFrameworkConfig)
                    serviceShape.getTrait<SigV4ATrait>()?.let {
                        write("service = #S", it.name)
                    }
                }
                write("provider(#S) { authenticate { ctx -> ctx.principal(Unit) } }", "no-auth")
            }
        }
    }
}
