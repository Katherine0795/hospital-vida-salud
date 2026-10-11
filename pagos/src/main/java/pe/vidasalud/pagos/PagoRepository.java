package pe.vidasalud.pagos;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import pe.vidasalud.common.SqlRepository;
import reactor.core.publisher.Mono;

@Repository
public class PagoRepository extends SqlRepository {
  public PagoRepository(DatabaseClient d) {
    super(d);
  }

  public Mono<Pago> find(UUID id) {
    return one(
        "SELECT * FROM pago WHERE id=:id",
        values("id", id),
        (r, m) ->
            new Pago(
                r.get("id", UUID.class),
                r.get("paciente_id", UUID.class),
                r.get("concepto", String.class),
                r.get("monto", BigDecimal.class),
                r.get("fecha", OffsetDateTime.class),
                r.get("medio", String.class),
                r.get("numero_operacion", String.class),
                r.get("estado", String.class)));
  }

  public Mono<Pago> insert(Pago p) {
    return execute(
            "INSERT INTO pago(id,paciente_id,concepto,monto,fecha,medio,numero_operacion,estado)"
                + " VALUES(:id,:p,:c,:m,:f,:me,:n,:e)",
            values(
                "id",
                p.id(),
                "p",
                p.pacienteId(),
                "c",
                p.concepto(),
                "m",
                p.monto(),
                "f",
                p.fecha(),
                "me",
                p.medio(),
                "n",
                p.numeroOperacion(),
                "e",
                p.estado()))
        .then(find(p.id()));
  }
}
