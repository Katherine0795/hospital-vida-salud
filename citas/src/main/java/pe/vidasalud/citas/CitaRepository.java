package pe.vidasalud.citas;

import io.r2dbc.spi.*;
import java.time.*;
import java.util.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import pe.vidasalud.common.SqlRepository;
import reactor.core.publisher.*;

@Repository
public class CitaRepository extends SqlRepository {
  public CitaRepository(DatabaseClient d) {
    super(d);
  }

  private Cita map(Row r, RowMetadata m) {
    return new Cita(
        r.get("id", UUID.class),
        r.get("paciente_id", UUID.class),
        r.get("especialidad", String.class),
        r.get("fecha_hora", OffsetDateTime.class),
        r.get("profesional_codigo", String.class),
        r.get("motivo", String.class),
        r.get("estado", String.class),
        r.get("fecha_creacion", OffsetDateTime.class),
        r.get("fecha_actualizacion", OffsetDateTime.class),
        r.get("version", Integer.class),
        r.get("atencion_id", UUID.class));
  }

  public Mono<Cita> find(UUID id) {
    return one("SELECT * FROM cita WHERE id=:id", values("id", id), this::map);
  }

  public Flux<Cita> list() {
    return many("SELECT * FROM cita ORDER BY fecha_hora LIMIT 100", Map.of(), this::map);
  }

  public Mono<Cita> insert(Cita c) {
    return execute(
            "INSERT INTO"
                + " cita(id,paciente_id,especialidad,fecha_hora,profesional_codigo,motivo,estado,fecha_creacion,fecha_actualizacion,version)"
                + " VALUES(:id,:p,:e,:f,:pr,:m,:s,:fc,:fa,0)",
            values(
                "id",
                c.id(),
                "p",
                c.pacienteId(),
                "e",
                c.especialidad(),
                "f",
                c.fechaHora(),
                "pr",
                c.profesionalCodigo(),
                "m",
                c.motivo(),
                "s",
                c.estado(),
                "fc",
                c.fechaCreacion(),
                "fa",
                c.fechaActualizacion()))
        .then(find(c.id()));
  }

  public Mono<Long> change(Cita old, String next, OffsetDateTime date, UUID atencionId) {
    return execute(
        "UPDATE cita SET estado=:s,fecha_actualizacion=:fa,version=version+1,atencion_id=:a WHERE"
            + " id=:id AND version=:v",
        values(
            "s",
            next,
            "fa",
            date,
            "a",
            atencionId == null ? nil(UUID.class) : atencionId,
            "id",
            old.id(),
            "v",
            old.version()));
  }

  public Mono<Long> history(UUID id, String prev, String next, OffsetDateTime date) {
    return execute(
        "INSERT INTO historial_cita(id,cita_id,estado_anterior,estado_nuevo,fecha,origen)"
            + " VALUES(:id,:c,:p,:n,:f,'citas')",
        values("id", UUID.randomUUID(), "c", id, "p", prev, "n", next, "f", date));
  }

  public Mono<Long> reschedule(
      Cita old, OffsetDateTime date, String professional, OffsetDateTime at) {
    return execute(
        "UPDATE cita SET"
            + " fecha_hora=:f,profesional_codigo=:p,fecha_actualizacion=:a,version=version+1 WHERE"
            + " id=:id AND version=:v",
        values("f", date, "p", professional, "a", at, "id", old.id(), "v", old.version()));
  }

  public Mono<Long> scheduleHistory(Cita old, Cita updated, OffsetDateTime at) {
    return execute(
        "INSERT INTO historial_cita(id,cita_id,estado_anterior,estado_nuevo,fecha,origen,detalle)"
            + " VALUES(:id,:c,:p,:n,:f,'citas',:d)",
        values(
            "id",
            UUID.randomUUID(),
            "c",
            old.id(),
            "p",
            old.estado(),
            "n",
            updated.estado(),
            "f",
            at,
            "d",
            "Agenda "
                + old.fechaHora()
                + "/"
                + old.profesionalCodigo()
                + " -> "
                + updated.fechaHora()
                + "/"
                + updated.profesionalCodigo()));
  }
}
