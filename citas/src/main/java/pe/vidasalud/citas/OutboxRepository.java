package pe.vidasalud.citas;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import pe.vidasalud.common.*;
import reactor.core.publisher.*;

@Repository
public class OutboxRepository extends SqlRepository {
  private final ObjectMapper json;

  public record Pending(UUID id, String routingKey, String payload) {}

  public OutboxRepository(DatabaseClient d, ObjectMapper json) {
    super(d);
    this.json = json;
  }

  public Mono<Long> append(HospitalEvent event, String key) {
    return Mono.fromCallable(() -> json.writeValueAsString(event))
        .flatMap(
            payload ->
                execute(
                    "INSERT INTO outbox(event_id,routing_key,payload,created_at)"
                        + " VALUES(:id,:k,:p,:f)",
                    values(
                        "id", event.eventId(), "k", key, "p", payload, "f", event.occurredAt())));
  }

  public Flux<Pending> pending() {
    return many(
        "SELECT * FROM outbox WHERE sent_at IS NULL ORDER BY created_at LIMIT 50",
        Map.of(),
        (r, m) ->
            new Pending(
                r.get("event_id", UUID.class),
                r.get("routing_key", String.class),
                r.get("payload", String.class)));
  }

  public Mono<Long> sent(UUID id) {
    return execute(
        "UPDATE outbox SET sent_at=CURRENT_TIMESTAMP WHERE event_id=:id", values("id", id));
  }
}
