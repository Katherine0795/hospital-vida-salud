package pe.vidasalud.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.test.StepVerifier;

@SpringBootTest(
    properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
class GatewayRoutesTest {
  @Autowired RouteDefinitionLocator routes;

  @Test
  void exposesSixServicesByDiscoveryName() {
    StepVerifier.create(routes.getRouteDefinitions().collectList())
        .expectNextMatches(
            list ->
                list.size() == 6
                    && list.stream().allMatch(r -> r.getUri().getScheme().equals("lb")))
        .verifyComplete();
  }
}
