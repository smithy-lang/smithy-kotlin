package smithy.kotlin.traits.auth

import aws.smithy.kotlin.runtime.auth.AuthOption

object DefaultLambdaAuthSchemeProvider : LambdaAuthSchemeProvider {
    override suspend fun resolveAuthScheme(params: LambdaAuthSchemeParameters): List<AuthOption> {
        error("not needed for paginator integration tests")
    }
}