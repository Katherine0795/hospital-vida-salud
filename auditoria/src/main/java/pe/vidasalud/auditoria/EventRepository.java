package pe.vidasalud.auditoria;

import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import pe.vidasalud.common.*;
import reactor.core.publisher.*;

@Repository
public class EventRepository extends SqlRepository {
  public EventRepository(DatabaseClient d) {
    super(d);
  }

  public Mono<Boolean> store(HospitalEvent e) {
    if (e.version() != 1
        || e.eventId() == null
        || e.citaId() == null
        || e.pacienteId() == null
        || e.occurredAt() == null
        || e.estadoNuevo() == null
        || e.eventType() == null
        || e.origen() == null)
      return Mono.error(new IllegalArgumentException("Contrato de evento invalido"));
    return execute(
            "INSERT INTO"
                + " evento(event_id,event_type,version,occurred_at,cita_id,paciente_id,estado_anterior,estado_nuevo,origen,received_at)"
                + " VALUES(:id,:t,:v,:f,:c,:p,:a,:n,:o,CURRENT_TIMESTAMP)",
            values(
                "id",
                e.eventId(),
                "t",
                e.eventType(),
                "v",
                e.version(),
                "f",
                e.occurredAt(),
                "c",
                e.citaId(),
                "p",
                e.pacienteId(),
                "a",
                e.estadoAnterior(),
                "n",
                e.estadoNuevo(),
                "o",
                e.origen()))
        .map(n -> true)
        .onErrorResume(DuplicateKeyException.class, ex -> Mono.just(false));
  }

  public Flux<HospitalEvent> list(UUID cita) {
    return many(
        "SELECT * FROM evento WHERE cita_id=:c ORDER BY occurred_at LIMIT 100",
        values("c", cita),
        (r, m) ->
            new HospitalEvent(
                r.get("event_id", UUID.class),
                r.get("event_type", String.class),
                r.get("version", Integer.class),
                r.get("occurred_at", OffsetDateTime.class),
                r.get("cita_id", UUID.class),
                r.get("paciente_id", UUID.class),
                r.get("estado_anterior", String.class),
                r.get("estado_nuevo", String.class),
                r.get("origen", String.class)));
  }
}
