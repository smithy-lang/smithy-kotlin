package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SymbolVisitorTest {

    // See https://awslabs.github.io/smithy/1.0/spec/aws/aws-core.html#using-sdk-service-id-for-client-naming
    @Test
    fun `it produces the correct string transformation for client names`() {
        assertEquals("ApiGateway", "API Gateway".clientName())
        assertEquals("Lambda", "Lambda".clientName())
        assertEquals("Elasticache", "ElastiCache".clientName())
        assertEquals("Apigatewaymanagementapi", "ApiGatewayManagementApi".clientName())
        assertEquals("MigrationhubConfig", "MigrationHub Config".clientName())
        assertEquals("Iotfleethub", "IoTFleetHub".clientName())
        assertEquals("Iot1clickProjects", "IoT 1Click Projects".clientName())
    }
}
