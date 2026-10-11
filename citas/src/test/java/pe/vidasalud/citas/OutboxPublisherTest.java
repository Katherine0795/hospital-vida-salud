package pe.vidasalud.citas;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import pe.vidasalud.common.HospitalEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OutboxPublisherTest {
  @Test
  void confirmsBeforeMarkingSent() throws Exception {
    var repo = mock(OutboxRepository.class);
    var rabbit = mock(RabbitTemplate.class);
    var json = new ObjectMapper().findAndRegisterModules();
    var id = UUID.randomUUID();
    var e =
        new HospitalEvent(
            id,
            "CitaConfirmada",
            1,
            OffsetDateTime.now(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "PENDIENTE",
            "CONFIRMADA",
            "citas");
    when(repo.sent(id)).thenReturn(Mono.just(1L));
    when(rabbit.invoke(any()))
        .thenAnswer(
            i ->
                ((org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?>)
                        i.getArgument(0))
                    .doInRabbit(rabbit));
    StepVerifier.create(
            new OutboxPublisher(repo, rabbit, json)
                .send(
                    new OutboxRepository.Pending(
                        id, "cita.confirmada.v1", json.writeValueAsString(e))))
        .verifyComplete();
    var order = inOrder(rabbit, repo);
    order
        .verify(rabbit)
        .convertAndSend(
            eq("hospital.events"),
            eq("cita.confirmada.v1"),
            org.mockito.Mockito.<Object>argThat(
                value ->
                    value instanceof HospitalEvent event
                        && event.eventId().equals(e.eventId())
                        && event.occurredAt().toInstant().equals(e.occurredAt().toInstant())));
    order.verify(rabbit).waitForConfirmsOrDie(3000);
    order.verify(repo).sent(id);
  }

  @Test
  void failedPublishRemainsPending() throws Exception {
    var repo = mock(OutboxRepository.class);
    var rabbit = mock(RabbitTemplate.class);
    var json = new ObjectMapper().findAndRegisterModules();
    var id = UUID.randomUUID();
    var e =
        new HospitalEvent(
            id,
            "CitaConfirmada",
            1,
            OffsetDateTime.now(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "PENDIENTE",
            "CONFIRMADA",
            "citas");
    when(rabbit.invoke(any())).thenThrow(new IllegalStateException("broker unavailable"));
    StepVerifier.create(
            new OutboxPublisher(repo, rabbit, json)
                .send(
                    new OutboxRepository.Pending(
                        id, "cita.confirmada.v1", json.writeValueAsString(e))))
        .expectError(IllegalStateException.class)
        .verify();
    verify(repo, never()).sent(any());
  }
}
