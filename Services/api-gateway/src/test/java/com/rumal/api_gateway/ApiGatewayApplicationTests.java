package com.rumal.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example.test/realms/rumal",
		"keycloak.jwk-set-uri=https://keycloak.example.test/realms/rumal/protocol/openid-connect/certs",
		"keycloak.audience=rumal-api",
		"keycloak.admin.client-id=test-gateway-admin",
		"keycloak.admin.client-secret=test-secret",
		"internal.auth.shared-secret=test-internal-secret",
		"eureka.client.enabled=false",
		"spring.cloud.discovery.enabled=false"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
