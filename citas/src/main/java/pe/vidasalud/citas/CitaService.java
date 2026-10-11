package pe.vidasalud.citas;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import pe.vidasalud.common.*;
import reactor.core.publisher.*;

@Service
public class CitaService {
  private final CitaRepository repo;
  private final OutboxRepository events;
  private final ServiceClients clients;
  private final TransactionalOperator tx;
  private final Clock clock;

  public CitaService(
      CitaRepository repo,
      OutboxRepository events,
      ServiceClients clients,
      org.springframework.transaction.ReactiveTransactionManager tm,
      Clock clock) {
    this.repo = repo;
    this.events = events;
    this.clients = clients;
    this.tx = TransactionalOperator.create(tm);
    this.clock = clock;
  }

  public Mono<Cita> get(UUID id) {
    return repo.find(id).switchIfEmpty(Mono.error(Errors.missing()));
  }

  public Flux<Cita> list() {
    return repo.list();
  }

  private Mono<Void> event(Cita c, String prev, String type, String key, OffsetDateTime at) {
    var ev =
        new HospitalEvent(
            UUID.randomUUID(), type, 1, at, c.id(), c.pacienteId(), prev, c.estado(), "citas");
    return repo.history(c.id(), prev, c.estado(), at).then(events.append(ev, key)).then();
  }

  public Mono<Cita> create(CitaInput i) {
    return Mono.defer(
        () -> {
          CitaRules.agenda(i.fechaHora(), clock);
          return clients
              .patient(i.pacienteId())
              .flatMap(
                  p -> {
                    if (!"ACTIVO".equals(p.estado()))
                      return Mono.error(Errors.conflict("Paciente inactivo"));
                    var at = OffsetDateTime.now(clock);
                    var c =
                        new Cita(
                            UUID.randomUUID(),
                            p.id(),
                            i.especialidad(),
                            i.fechaHora().withOffsetSameInstant(ZoneOffset.UTC),
                            i.profesionalCodigo(),
                            i.motivo(),
                            "PENDIENTE",
                            at,
                            at,
                            0,
                            null);
                    return tx.transactional(
                        repo.insert(c)
                            .flatMap(
                                saved ->
                                    event(saved, null, "CitaRegistrada", "cita.registrada.v1", at)
                                        .thenReturn(saved)));
                  });
        });
  }

  public Mono<Cita> change(UUID id, EstadoInput input) {
    return get(id)
        .flatMap(
            old -> {
              if ("ATENDIDA".equals(old.estado())
                  && "ATENDIDA".equals(input.estado())
                  && input.atencionId() != null
                  && input.atencionId().equals(old.atencionId())) return Mono.just(old);
              CitaRules.transition(old.estado(), input.estado(), input.atencionId());
              var at = OffsetDateTime.now(clock);
              String type =
                  switch (input.estado()) {
                    case "CONFIRMADA" -> "CitaConfirmada";
                    case "CANCELADA" -> "CitaCancelada";
                    case "ATENDIDA" -> "CitaAtendida";
                    default -> "CitaAusente";
                  };
              Mono<Void> reference =
                  "ATENDIDA".equals(input.estado())
                      ? clients
                          .attention(input.atencionId())
                          .flatMap(
                              a -> {
                                if (!a.citaId().equals(id)
                                    || !a.pacienteId().equals(old.pacienteId()))
                                  return Mono.error(
                                      Errors.conflict("Atencion no pertenece a la cita"));
                                return Mono.<Void>empty();
                              })
                      : Mono.empty();
              return reference.then(
                  tx.transactional(
                      repo.change(old, input.estado(), at, input.atencionId())
                          .flatMap(
                              n -> {
                                if (n != 1)
                                  return Mono.error(
                                      Errors.conflict("Cita modificada por otra solicitud"));
                                return repo.find(id);
                              })
                          .flatMap(
                              saved ->
                                  event(
                                          saved,
                                          old.estado(),
                                          type,
                                          "cita." + input.estado().toLowerCase(Locale.ROOT) + ".v1",
                                          at)
                                      .thenReturn(saved))));
            });
  }

  public Mono<Cita> reschedule(UUID id, ReprogramacionInput input) {
    return get(id)
        .flatMap(
            old -> {
              CitaRules.agenda(input.fechaHora(), clock);
              CitaRules.reschedule(old.estado());
              var at = OffsetDateTime.now(clock);
              var date = input.fechaHora().withOffsetSameInstant(ZoneOffset.UTC);
              return tx.transactional(
                  repo.reschedule(old, date, input.profesionalCodigo(), at)
                      .flatMap(
                          n -> {
                            if (n != 1)
                              return Mono.error(
                                  Errors.conflict("Cita modificada por otra solicitud"));
                            return repo.find(id);
                          })
                      .flatMap(
                          updated -> {
                            var event =
                                new HospitalEvent(
                                    UUID.randomUUID(),
                                    "CitaReprogramada",
                                    1,
                                    at,
                                    id,
                                    old.pacienteId(),
                                    old.estado(),
                                    updated.estado(),
                                    "citas");
                            return repo.scheduleHistory(old, updated, at)
                                .then(events.append(event, "cita.reprogramada.v1"))
                                .thenReturn(updated);
                          }));
            });
  }
}
