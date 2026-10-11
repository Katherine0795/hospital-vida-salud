package pe.vidasalud.pacientes;

import static org.mockito.Mockito.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PacienteServiceTest {
  PacienteRepository repo = mock(PacienteRepository.class);
  Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  PacienteService s = new PacienteService(repo, clock);
  PacienteInput input =
      new PacienteInput(
          "OTRO",
          "DEMO-001",
          "Ana",
          "Demo",
          LocalDate.of(1990, 1, 1),
          "000000000",
          "demo@example.test",
          null,
          null);

  @Test
  void registersWithUniqueIdAndActiveState() {
    when(repo.insert(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
    StepVerifier.create(s.create(input))
        .expectNextMatches(
            p ->
                p.id() != null
                    && p.estado().equals("ACTIVO")
                    && p.fechaRegistro().toInstant().equals(clock.instant()))
        .verifyComplete();
  }

  @Test
  void propagatesDuplicateConstraint() {
    when(repo.insert(any()))
        .thenReturn(Mono.error(new org.springframework.dao.DuplicateKeyException("duplicate")));
    StepVerifier.create(s.create(input))
        .expectError(org.springframework.dao.DuplicateKeyException.class)
        .verify();
  }

  @Test
  void absentResourceIs404() {
    when(repo.find(any())).thenReturn(Mono.empty());
    StepVerifier.create(s.get(UUID.randomUUID()))
        .expectErrorMatches(
            e ->
                e instanceof org.springframework.web.server.ResponseStatusException r
                    && r.getStatusCode().value() == 404)
        .verify();
  }
}
