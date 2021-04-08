package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SymbolVisitorTest {

    @Test
    fun `it produces the correct string transformation for client names`() {
        // See https://awslabs.github.io/smithy/1.0/spec/aws/aws-core.html#using-sdk-service-id-for-client-naming
        assertEquals("ApiGateway", "API Gateway".clientName())
        assertEquals("Lambda", "Lambda".clientName())
        assertEquals("ElastiCache", "ElastiCache".clientName())
        assertEquals("ApiGatewayManagementApi", "ApiGatewayManagementApi".clientName())
        assertEquals("MigrationHubConfig", "MigrationHub Config".clientName())
        assertEquals("IoTFleetHub", "IoTFleetHub".clientName())
        assertEquals("Iot1ClickProjects", "IoT 1Click Projects".clientName())
        assertEquals("DynamoDb", "DynamoDB".clientName())
    }
}
