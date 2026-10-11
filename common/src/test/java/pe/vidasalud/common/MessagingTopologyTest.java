package pe.vidasalud.common;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;

class MessagingTopologyTest {
  @Test
  void durableConsumersAreBoundBeforePublication() {
    var ds = new MessagingConfiguration().hospitalTopology().getDeclarables();
    assertThat(
            ds.stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .filter(q -> q.getName().endsWith(".cita.v1")))
        .hasSize(2)
        .allMatch(Queue::isDurable);
    assertThat(
            ds.stream()
                .filter(Binding.class::isInstance)
                .map(Binding.class::cast)
                .filter(b -> b.getExchange().equals("hospital.events")))
        .hasSize(2)
        .allMatch(b -> b.getRoutingKey().equals("cita.*.v1"));
  }
}
