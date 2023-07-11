package software.amazon.smithy.kotlin.codegen.customization.s3

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.defaultSettings
import software.amazon.smithy.kotlin.codegen.test.prependNamespaceAndService
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.Model

class UserSuppliedRegionForCredentialsProvidersTest {
    @Test
    fun notS3ModelIntegration(){
        val model = sampleModel("not s3")
        val isEnabledForModel = UserSuppliedRegionForCredentialsProviders().enabledForService(model, model.defaultSettings())
        assertFalse(isEnabledForModel)
    }

    @Test
    fun s3ModelIntegration(){
        val model = sampleModel("s3")
        val isEnabledForModel = UserSuppliedRegionForCredentialsProviders().enabledForService(model, model.defaultSettings())
        assertTrue(isEnabledForModel)
    }
}

private fun sampleModel(serviceName: String): Model =
    """
        @http(method: "PUT", uri: "/foo")
        operation Foo { }
        
        @http(method: "POST", uri: "/bar")
        operation Bar { }
    """
        .prependNamespaceAndService(operations = listOf("Foo", "Bar"), serviceName = serviceName)
        .toSmithyModel()