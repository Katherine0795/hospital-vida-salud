package pe.vidasalud.citas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.vidasalud.common.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final OutboxRepository repo;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;

  public OutboxPublisher(OutboxRepository repo, RabbitTemplate rabbit, ObjectMapper json) {
    this.repo = repo;
    this.rabbit = rabbit;
    this.json = json;
  }

  public Mono<Void> send(OutboxRepository.Pending p) {
    return Mono.fromCallable(
            () -> {
              HospitalEvent e = json.readValue(p.payload(), HospitalEvent.class);
              rabbit.invoke(
                  ops -> {
                    ops.convertAndSend(MessagingConfiguration.EXCHANGE, p.routingKey(), e);
                    ops.waitForConfirmsOrDie(3000);
                    return true;
                  });
              return true;
            })
        .subscribeOn(Schedulers.boundedElastic())
        .then(Mono.defer(() -> repo.sent(p.id())))
        .then();
  }

  @Scheduled(fixedDelay = 2000, initialDelay = 3000)
  public Mono<Void> drain() {
    return Mono.defer(
        () ->
            repo.pending()
                .concatMap(
                    p ->
                        send(p)
                            .onErrorResume(
                                e -> {
                                  log.warn(
                                      "Publicacion pendiente; reintento posterior para evento {}",
                                      p.id());
                                  return Mono.empty();
                                }))
                .then());
  }
}
