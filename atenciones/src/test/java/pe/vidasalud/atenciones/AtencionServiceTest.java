package pe.vidasalud.atenciones;

import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.vidasalud.common.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AtencionServiceTest {
  AtencionRepository repo = mock(AtencionRepository.class);
  ServiceClients clients = mock(ServiceClients.class);
  AtencionService s = new AtencionService(repo, clients, Clock.systemUTC());

  @Test
  void rejectsUnconfirmedCita() {
    UUID id = UUID.randomUUID();
    when(repo.byCita(id)).thenReturn(Mono.empty());
    when(clients.cita(id))
        .thenReturn(
            Mono.just(new ServiceClients.CitaRef(id, UUID.randomUUID(), "PENDIENTE", null)));
    StepVerifier.create(s.create(new AtencionInput(id, "DEMO")))
        .expectErrorMatches(
            e ->
                e instanceof org.springframework.web.server.ResponseStatusException r
                    && r.getStatusCode().value() == 409)
        .verify();
    verify(repo, never()).insert(any());
  }

  @Test
  void createsAndClosesConfirmedCita() {
    UUID id = UUID.randomUUID(), patient = UUID.randomUUID();
    when(repo.byCita(id)).thenReturn(Mono.empty());
    when(clients.cita(id))
        .thenReturn(Mono.just(new ServiceClients.CitaRef(id, patient, "CONFIRMADA", null)));
    when(repo.insert(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
    when(clients.closeCita(eq(id), any()))
        .thenReturn(
            Mono.just(new ServiceClients.CitaRef(id, patient, "ATENDIDA", UUID.randomUUID())));
    when(repo.complete(any()))
        .thenAnswer(
            i ->
                Mono.just(
                    new Atencion(
                        i.getArgument(0),
                        patient,
                        id,
                        OffsetDateTime.now(),
                        "DEMO",
                        "REGISTRADA")));
    StepVerifier.create(s.create(new AtencionInput(id, "DEMO")))
        .expectNextMatches(a -> a.estado().equals("REGISTRADA"))
        .verifyComplete();
  }

  @Test
  void retriesPendingClosureWithoutSecondInsert() {
    var a =
        new Atencion(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            OffsetDateTime.now(),
            "DEMO",
            "PENDIENTE_CIERRE");
    when(repo.byCita(a.citaId())).thenReturn(Mono.just(a));
    when(clients.closeCita(a.citaId(), a.id()))
        .thenReturn(
            Mono.just(new ServiceClients.CitaRef(a.citaId(), a.pacienteId(), "ATENDIDA", a.id())));
    when(repo.complete(a.id()))
        .thenReturn(
            Mono.just(
                new Atencion(
                    a.id(), a.pacienteId(), a.citaId(), a.fecha(), a.area(), "REGISTRADA")));
    StepVerifier.create(s.create(new AtencionInput(a.citaId(), "DEMO")))
        .expectNextMatches(x -> x.id().equals(a.id()))
        .verifyComplete();
    verify(repo, never()).insert(any());
    verify(clients, never()).cita(any());
  }
}
