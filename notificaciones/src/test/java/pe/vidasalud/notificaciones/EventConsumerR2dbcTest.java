package pe.vidasalud.notificaciones;

import io.r2dbc.spi.ConnectionFactories;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import pe.vidasalud.common.HospitalEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class EventConsumerR2dbcTest {
  @Test
  void persistsOnceAndRejectsUnknownVersion() throws Exception {
    var db =
        DatabaseClient.create(
            ConnectionFactories.get(
                "r2dbc:h2:mem:///e" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
    var schema = new String(getClass().getResourceAsStream("/schema.sql").readAllBytes());
    StepVerifier.create(
            Flux.fromArray(schema.split(";"))
                .filter(s -> !s.isBlank())
                .concatMap(s -> db.sql(s).then())
                .then())
        .verifyComplete();
    var repo = new EventRepository(db);
    var consumer = new EventConsumer(repo);
    var e =
        new HospitalEvent(
            UUID.randomUUID(),
            "CitaConfirmada",
            1,
            OffsetDateTime.now(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "PENDIENTE",
            "CONFIRMADA",
            "citas");
    StepVerifier.create(consumer.consume(e)).verifyComplete();
    StepVerifier.create(consumer.consume(e)).verifyComplete();
    StepVerifier.create(repo.list(e.citaId()).collectList())
        .expectNextMatches(list -> list.size() == 1 && list.get(0).eventId().equals(e.eventId()))
        .verifyComplete();
    var bad =
        new HospitalEvent(
            UUID.randomUUID(),
            "CitaConfirmada",
            2,
            e.occurredAt(),
            e.citaId(),
            e.pacienteId(),
            e.estadoAnterior(),
            e.estadoNuevo(),
            e.origen());
    StepVerifier.create(consumer.consume(bad)).expectError(IllegalArgumentException.class).verify();
  }
}
