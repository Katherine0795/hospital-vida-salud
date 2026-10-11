package pe.vidasalud.citas;

import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import pe.vidasalud.common.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CitaServiceTest {
  CitaRepository repo = mock(CitaRepository.class);
  OutboxRepository out = mock(OutboxRepository.class);
  ServiceClients clients = mock(ServiceClients.class);
  CitaService s =
      new CitaService(
          repo,
          out,
          clients,
          mock(org.springframework.transaction.ReactiveTransactionManager.class),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void missingPatientStopsCreation() {
    var id = UUID.randomUUID();
    when(clients.patient(id)).thenReturn(Mono.error(Errors.missing()));
    StepVerifier.create(
            s.create(
                new CitaInput(
                    id, "DEMO", OffsetDateTime.parse("2027-01-01T00:30:00Z"), "DEMO", "Demo")))
        .expectError(org.springframework.web.server.ResponseStatusException.class)
        .verify();
    verifyNoInteractions(repo, out);
  }

  @Test
  void inactivePatientStopsCreation() {
    var id = UUID.randomUUID();
    when(clients.patient(id)).thenReturn(Mono.just(new ServiceClients.PatientRef(id, "INACTIVO")));
    StepVerifier.create(
            s.create(
                new CitaInput(
                    id, "DEMO", OffsetDateTime.parse("2027-01-01T00:30:00Z"), "DEMO", "Demo")))
        .expectErrorMatches(
            e ->
                e instanceof org.springframework.web.server.ResponseStatusException r
                    && r.getStatusCode().value() == 409)
        .verify();
    verifyNoInteractions(repo, out);
  }

  @Test
  void canceledCannotBeConfirmed() {
    var at = OffsetDateTime.now();
    var c =
        new Cita(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "DEMO",
            at,
            "DEMO",
            "Demo",
            "CANCELADA",
            at,
            at,
            1,
            null);
    when(repo.find(c.id())).thenReturn(Mono.just(c));
    StepVerifier.create(s.change(c.id(), new EstadoInput("CONFIRMADA", null)))
        .expectErrorMatches(
            e ->
                e instanceof org.springframework.web.server.ResponseStatusException r
                    && r.getStatusCode().value() == 409)
        .verify();
    verify(repo, never()).change(any(), anyString(), any(), any());
  }
}
