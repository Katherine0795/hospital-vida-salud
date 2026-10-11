package pe.vidasalud.common;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ServiceClientsTest {
  private ServiceClients client(int code, String body) {
    return new ServiceClients(
        WebClient.builder()
            .exchangeFunction(
                r ->
                    Mono.just(
                        ClientResponse.create(HttpStatus.valueOf(code))
                            .header("Content-Type", "application/json")
                            .body(body)
                            .build()))
            .build());
  }

  @Test
  void readsPatient() {
    UUID id = UUID.randomUUID();
    StepVerifier.create(client(200, "{\"id\":\"" + id + "\",\"estado\":\"ACTIVO\"}").patient(id))
        .expectNextMatches(p -> p.id().equals(id) && p.estado().equals("ACTIVO"))
        .verifyComplete();
  }

  @Test
  void missingReferenceIs404() {
    StepVerifier.create(client(404, "{}").patient(UUID.randomUUID()))
        .expectErrorMatches(
            e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 404)
        .verify();
  }

  @Test
  void unavailableIs503() {
    StepVerifier.create(client(500, "{}").cita(UUID.randomUUID()))
        .expectErrorMatches(
            e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 503)
        .verify();
  }

  @Test
  void incompatibleIs409() {
    StepVerifier.create(client(409, "{}").closeCita(UUID.randomUUID(), UUID.randomUUID()))
        .expectErrorMatches(
            e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 409)
        .verify();
  }

  @Test
  void deadlineIs504() {
    var s = new ServiceClients(WebClient.builder().exchangeFunction(r -> Mono.never()).build());
    StepVerifier.withVirtualTime(() -> s.patient(UUID.randomUUID()))
        .thenAwait(java.time.Duration.ofSeconds(3))
        .expectErrorMatches(
            e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 504)
        .verify();
  }
}
