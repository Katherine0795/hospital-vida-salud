package pe.vidasalud.common;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.*;

@Configuration
public class MessagingConfiguration {
  public static final String EXCHANGE = "hospital.events";

  @Bean
  TopicExchange hospitalExchange() {
    return new TopicExchange(EXCHANGE, true, false);
  }

  @Bean
  DirectExchange errorsExchange() {
    return new DirectExchange("hospital.errors", true, false);
  }

  @Bean
  Jackson2JsonMessageConverter eventConverter() {
    return new Jackson2JsonMessageConverter("pe.vidasalud.common");
  }

  @Bean
  Declarables hospitalTopology() {
    java.util.List<Declarable> all = new java.util.ArrayList<>();
    for (String service : java.util.List.of("notificaciones", "auditoria")) {
      Queue queue =
          QueueBuilder.durable(service + ".cita.v1")
              .deadLetterExchange("hospital.errors")
              .deadLetterRoutingKey(service + ".error")
              .build();
      Queue failed = QueueBuilder.durable(service + ".errores.v1").build();
      all.add(queue);
      all.add(failed);
      all.add(
          new Binding(queue.getName(), Binding.DestinationType.QUEUE, EXCHANGE, "cita.*.v1", null));
      all.add(
          new Binding(
              failed.getName(),
              Binding.DestinationType.QUEUE,
              "hospital.errors",
              service + ".error",
              null));
    }
    return new Declarables(all);
  }

  @Bean
  RabbitTemplate eventTemplate(ConnectionFactory c, Jackson2JsonMessageConverter converter) {
    RabbitTemplate t = new RabbitTemplate(c);
    t.setMessageConverter(converter);
    t.setMandatory(true);
    return t;
  }
}
