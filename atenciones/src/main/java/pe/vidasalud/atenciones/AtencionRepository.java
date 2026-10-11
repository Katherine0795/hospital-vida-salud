package pe.vidasalud.atenciones;

import io.r2dbc.spi.*;
import java.time.*;
import java.util.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import pe.vidasalud.common.SqlRepository;
import reactor.core.publisher.Mono;

@Repository
public class AtencionRepository extends SqlRepository {
  public AtencionRepository(DatabaseClient d) {
    super(d);
  }

  private Atencion map(Row r, RowMetadata m) {
    return new Atencion(
        r.get("id", UUID.class),
        r.get("paciente_id", UUID.class),
        r.get("cita_id", UUID.class),
        r.get("fecha", OffsetDateTime.class),
        r.get("area", String.class),
        r.get("estado", String.class));
  }

  public Mono<Atencion> find(UUID id) {
    return one("SELECT * FROM atencion WHERE id=:id", values("id", id), this::map);
  }

  public Mono<Atencion> byCita(UUID id) {
    return one("SELECT * FROM atencion WHERE cita_id=:id", values("id", id), this::map);
  }

  public Mono<Atencion> insert(Atencion a) {
    return execute(
            "INSERT INTO atencion(id,paciente_id,cita_id,fecha,area,estado)"
                + " VALUES(:id,:p,:c,:f,:a,:e)",
            values(
                "id",
                a.id(),
                "p",
                a.pacienteId(),
                "c",
                a.citaId(),
                "f",
                a.fecha(),
                "a",
                a.area(),
                "e",
                a.estado()))
        .then(find(a.id()));
  }

  public Mono<Atencion> complete(UUID id) {
    return execute("UPDATE atencion SET estado='REGISTRADA' WHERE id=:id", values("id", id))
        .then(find(id));
  }
}
