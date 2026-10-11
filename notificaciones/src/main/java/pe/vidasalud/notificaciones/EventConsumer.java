package pe.vidasalud.notificaciones;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pe.vidasalud.common.HospitalEvent;
import reactor.core.publisher.Mono;

@Component
public class EventConsumer {
  private final EventRepository repo;

  public EventConsumer(EventRepository repo) {
    this.repo = repo;
  }

  @RabbitListener(queues = "notificaciones.cita.v1")
  public Mono<Void> consume(HospitalEvent event) {
    return repo.store(event)
        .retryWhen(
            reactor.util.retry.Retry.fixedDelay(2, java.time.Duration.ofMillis(200))
                .filter(e -> e instanceof org.springframework.dao.DataAccessException))
        .then();
  }
}
