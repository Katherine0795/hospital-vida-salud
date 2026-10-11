package pe.vidasalud.pacientes;

import io.r2dbc.spi.ConnectionFactories;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PacienteR2dbcTest {
  @Test
  void persistsReadsAndRejectsDuplicateWithoutJdbc() throws Exception {
    var db =
        DatabaseClient.create(
            ConnectionFactories.get(
                "r2dbc:h2:mem:///p" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"));
    String schema =
        new String(
            getClass().getResourceAsStream("/schema.sql").readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    StepVerifier.create(
            Flux.fromArray(schema.split(";"))
                .filter(s -> !s.isBlank())
                .concatMap(s -> db.sql(s).then())
                .then())
        .verifyComplete();
    var repo = new PacienteRepository(db);
    var at = OffsetDateTime.now(ZoneOffset.UTC);
    UUID id = UUID.randomUUID();
    var p =
        new Paciente(
            id,
            "OTRO",
            "DB-DEMO",
            "Ana",
            "Demo",
            LocalDate.of(1990, 1, 1),
            "000000000",
            "demo@example.test",
            null,
            at,
            "ACTIVO");
    StepVerifier.create(repo.insert(p))
        .expectNextMatches(x -> x.id().equals(id) && x.direccion() == null)
        .verifyComplete();
    StepVerifier.create(repo.find(id))
        .expectNextMatches(x -> x.numeroDocumento().equals("DB-DEMO"))
        .verifyComplete();
    var duplicate =
        new Paciente(
            UUID.randomUUID(),
            "OTRO",
            "DB-DEMO",
            "Otra",
            "Demo",
            p.fechaNacimiento(),
            p.telefono(),
            p.correo(),
            null,
            at,
            "ACTIVO");
    StepVerifier.create(repo.insert(duplicate))
        .expectError(org.springframework.dao.DataIntegrityViolationException.class)
        .verify();
  }
}
