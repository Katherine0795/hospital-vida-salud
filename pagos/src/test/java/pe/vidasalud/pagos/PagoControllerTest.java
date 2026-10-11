package pe.vidasalud.pagos;

import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import pe.vidasalud.common.Errors;

class PagoControllerTest {
  PagoService s = mock(PagoService.class);
  WebTestClient h =
      WebTestClient.bindToController(new PagoController(s)).controllerAdvice(new Errors()).build();

  @Test
  void negativePaymentIs400() {
    h.post()
        .uri("/api/pagos")
        .bodyValue(
            Map.of(
                "pacienteId",
                UUID.randomUUID().toString(),
                "concepto",
                "Demo",
                "monto",
                -10,
                "medio",
                "EFECTIVO",
                "numeroOperacion",
                "DEMO-01"))
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(s);
  }

  @Test
  void zeroPaymentIs400() {
    h.post()
        .uri("/api/pagos")
        .bodyValue(
            Map.of(
                "pacienteId",
                UUID.randomUUID().toString(),
                "concepto",
                "Demo",
                "monto",
                0,
                "medio",
                "EFECTIVO",
                "numeroOperacion",
                "DEMO-02"))
        .exchange()
        .expectStatus()
        .isBadRequest();
    verifyNoInteractions(s);
  }
}
