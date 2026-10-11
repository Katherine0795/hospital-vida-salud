package pe.vidasalud.pacientes;

import io.r2dbc.spi.*;
import java.time.*;
import java.util.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import pe.vidasalud.common.SqlRepository;
import reactor.core.publisher.*;

@Repository
public class PacienteRepository extends SqlRepository {
  public PacienteRepository(DatabaseClient d) {
    super(d);
  }

  private Paciente map(Row r, RowMetadata m) {
    return new Paciente(
        r.get("id", UUID.class),
        r.get("tipo_documento", String.class),
        r.get("numero_documento", String.class),
        r.get("nombres", String.class),
        r.get("apellidos", String.class),
        r.get("fecha_nacimiento", LocalDate.class),
        r.get("telefono", String.class),
        r.get("correo", String.class),
        r.get("direccion", String.class),
        r.get("fecha_registro", OffsetDateTime.class),
        r.get("estado", String.class));
  }

  public Mono<Paciente> find(UUID id) {
    return one("SELECT * FROM paciente WHERE id=:id", values("id", id), this::map);
  }

  public Flux<Paciente> list() {
    return many(
        "SELECT * FROM paciente ORDER BY fecha_registro DESC LIMIT 100", Map.of(), this::map);
  }

  private Map<String, Object> params(Paciente p) {
    return values(
        "id",
        p.id(),
        "td",
        p.tipoDocumento(),
        "nd",
        p.numeroDocumento(),
        "n",
        p.nombres(),
        "a",
        p.apellidos(),
        "fn",
        p.fechaNacimiento(),
        "t",
        p.telefono(),
        "c",
        p.correo(),
        "d",
        p.direccion(),
        "fr",
        p.fechaRegistro(),
        "e",
        p.estado());
  }

  public Mono<Paciente> insert(Paciente p) {
    return execute(
            "INSERT INTO"
                + " paciente(id,tipo_documento,numero_documento,nombres,apellidos,fecha_nacimiento,telefono,correo,direccion,fecha_registro,estado)"
                + " VALUES(:id,:td,:nd,:n,:a,:fn,:t,:c,:d,:fr,:e)",
            params(p))
        .then(find(p.id()));
  }

  public Mono<Paciente> update(Paciente p) {
    var v = params(p);
    v.remove("fr");
    return execute(
            "UPDATE paciente SET"
                + " tipo_documento=:td,numero_documento=:nd,nombres=:n,apellidos=:a,fecha_nacimiento=:fn,telefono=:t,correo=:c,direccion=:d,estado=:e"
                + " WHERE id=:id",
            v)
        .then(find(p.id()));
  }
}
