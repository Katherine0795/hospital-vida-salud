package pe.vidasalud.citas;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.ConnectionFactories;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import pe.vidasalud.common.*;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;

class CitaR2dbcTest {
  @Test
  void transactionRollsBackWhenOutboxFailsAndRejectsStaleVersion() throws Exception {
    var factory =
        ConnectionFactories.get("r2dbc:h2:mem:///c" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    var db = DatabaseClient.create(factory);
    String schema =
        new String(getClass().getResourceAsStream("/schema.sql").readAllBytes())
            .replaceAll("(?m)CREATE (?:UNIQUE )?INDEX[^;]+WHERE[^;]+;", "");
    StepVerifier.create(
            Flux.fromArray(schema.split(";"))
                .filter(s -> !s.isBlank())
                .concatMap(s -> db.sql(s).then())
                .then())
        .verifyComplete();
    var repo = new CitaRepository(db);
    var outbox = new OutboxRepository(db, new ObjectMapper().findAndRegisterModules());
    var clients = mock(ServiceClients.class);
    UUID patient = UUID.randomUUID();
    when(clients.patient(patient))
        .thenReturn(Mono.just(new ServiceClients.PatientRef(patient, "ACTIVO")));
    var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    var s = new CitaService(repo, outbox, clients, new R2dbcTransactionManager(factory), clock);
    var input =
        new CitaInput(
            patient,
            "DEMO",
            OffsetDateTime.parse("2027-01-01T00:30:00Z"),
            "DEMO-01",
            "Prueba administrativa");
    var created = s.create(input).cache();
    StepVerifier.create(created)
        .expectNextMatches(c -> c.estado().equals("PENDIENTE"))
        .verifyComplete();
    StepVerifier.create(created.flatMap(c -> s.change(c.id(), new EstadoInput("CONFIRMADA", null))))
        .expectNextMatches(c -> c.estado().equals("CONFIRMADA") && c.version() == 1)
        .verifyComplete();
    StepVerifier.create(
            created.flatMap(c -> repo.change(c, "CANCELADA", OffsetDateTime.now(), null)))
        .expectNext(0L)
        .verifyComplete();
    StepVerifier.create(outbox.pending().collectList())
        .expectNextMatches(l -> l.size() == 2)
        .verifyComplete();
    StepVerifier.create(
            created.flatMap(
                c ->
                    s.reschedule(
                        c.id(),
                        new ReprogramacionInput(
                            input.fechaHora().plusDays(2), "DEMO-REPROGRAMADO"))))
        .expectNextMatches(
            c ->
                c.version() == 2
                    && c.profesionalCodigo().equals("DEMO-REPROGRAMADO")
                    && c.estado().equals("CONFIRMADA"))
        .verifyComplete();
    StepVerifier.create(outbox.pending().collectList())
        .expectNextMatches(l -> l.size() == 3)
        .verifyComplete();
    var broken = mock(OutboxRepository.class);
    when(broken.append(any(), anyString()))
        .thenReturn(Mono.error(new IllegalStateException("outbox unavailable")));
    var failure =
        new CitaService(repo, broken, clients, new R2dbcTransactionManager(factory), clock);
    StepVerifier.create(
            failure.create(
                new CitaInput(patient, "DEMO", input.fechaHora().plusDays(1), "DEMO-02", "Prueba")))
        .expectError(IllegalStateException.class)
        .verify();
    StepVerifier.create(repo.list().collectList())
        .expectNextMatches(l -> l.size() == 1)
        .verifyComplete();
  }
}
