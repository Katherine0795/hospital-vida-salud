package pe.vidasalud.pacientes;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "eureka.client.enabled=false",
      "spring.cloud.discovery.enabled=false",
      "spring.r2dbc.url=r2dbc:h2:mem:///patientapp;DB_CLOSE_DELAY=-1",
      "spring.r2dbc.username=sa",
      "spring.r2dbc.password="
    })
@AutoConfigureWebTestClient
class PacienteApplicationTest {
  @Autowired WebTestClient http;

  @Test
  void completeHttpRequestUsesRealReactivePersistence() {
    Map<String, Object> input =
        Map.of(
            "tipoDocumento",
            "OTRO",
            "numeroDocumento",
            "APP-" + UUID.randomUUID().toString().substring(0, 8),
            "nombres",
            "Paciente",
            "apellidos",
            "Demostracion",
            "fechaNacimiento",
            "1990-01-01",
            "telefono",
            "000000000",
            "correo",
            "demo@example.test");
    var created =
        http.post()
            .uri("/api/pacientes")
            .bodyValue(input)
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(Paciente.class)
            .returnResult()
            .getResponseBody();
    org.assertj.core.api.Assertions.assertThat(created).isNotNull();
    http.get()
        .uri("/api/pacientes/" + created.id())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.numeroDocumento")
        .isEqualTo(input.get("numeroDocumento"));
    http.post().uri("/api/pacientes").bodyValue(input).exchange().expectStatus().isEqualTo(409);
  }
}
