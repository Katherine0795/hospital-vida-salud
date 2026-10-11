package pe.vidasalud.pacientes;

import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import pe.vidasalud.common.Errors;
import reactor.core.publisher.Mono;

class PacienteControllerTest {
  PacienteService service = mock(PacienteService.class);
  WebTestClient http =
      WebTestClient.bindToController(new PacienteController(service))
          .controllerAdvice(new Errors())
          .build();
  Map<String, Object> input =
      Map.of(
          "tipoDocumento",
          "OTRO",
          "numeroDocumento",
          "DEMO-001",
          "nombres",
          "Ana",
          "apellidos",
          "Demo",
          "fechaNacimiento",
          "1990-01-01",
          "telefono",
          "000000000",
          "correo",
          "demo@example.test");

  @Test
  void validRegistrationIs201() {
    UUID id = UUID.randomUUID();
    when(service.create(any()))
        .thenReturn(
            Mono.just(
                new Paciente(
                    id,
                    "OTRO",
                    "DEMO-001",
                    "Ana",
                    "Demo",
                    LocalDate.of(1990, 1, 1),
                    "000000000",
                    "demo@example.test",
                    null,
                    OffsetDateTime.now(),
                    "ACTIVO")));
    http.post()
        .uri("/api/pacientes")
        .bodyValue(input)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueEquals("Location", "/api/pacientes/" + id)
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(id.toString());
  }

  @Test
  void missingFieldsAre400() {
    http.post().uri("/api/pacientes").bodyValue(Map.of()).exchange().expectStatus().isBadRequest();
    verifyNoInteractions(service);
  }

  @Test
  void badEmailIs400() {
    var bad = new HashMap<>(input);
    bad.put("correo", "invalid");
    http.post().uri("/api/pacientes").bodyValue(bad).exchange().expectStatus().isBadRequest();
  }

  @Test
  void duplicateIs409() {
    when(service.create(any()))
        .thenReturn(
            Mono.error(new org.springframework.dao.DuplicateKeyException("secret database info")));
    http.post()
        .uri("/api/pacientes")
        .bodyValue(input)
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("Documento, agenda o referencia duplicada");
  }

  @Test
  void missingPatientIs404() {
    when(service.get(any())).thenReturn(Mono.error(Errors.missing()));
    http.get().uri("/api/pacientes/" + UUID.randomUUID()).exchange().expectStatus().isNotFound();
  }

  @Test
  void malformedIdIs400() {
    http.get().uri("/api/pacientes/no-es-uuid").exchange().expectStatus().isBadRequest();
  }
}
