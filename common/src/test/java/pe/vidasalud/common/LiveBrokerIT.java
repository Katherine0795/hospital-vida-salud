package pe.vidasalud.common;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;

/** Optional: -pl common test -Dtest=LiveBrokerIT with a real demonstration broker. */
class LiveBrokerIT {
  @Test
  void publishesConfirmedEventToTwoRealQueues() {
    String user = System.getenv("LIVE_RABBIT_USER"),
        password = System.getenv("LIVE_RABBIT_PASSWORD");
    assertThat(user).as("LIVE_RABBIT_USER required").isNotBlank();
    assertThat(password).as("LIVE_RABBIT_PASSWORD required").isNotBlank();
    var cf =
        new CachingConnectionFactory(System.getenv().getOrDefault("LIVE_RABBIT_HOST", "localhost"));
    cf.setUsername(user);
    cf.setPassword(password);
    cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
    var admin = new RabbitAdmin(cf);
    var cfg = new MessagingConfiguration();
    admin.declareExchange(cfg.hospitalExchange());
    String suffix = UUID.randomUUID().toString();
    var q1 = new Queue("t2.probe.notificaciones." + suffix, false, true, true);
    var q2 = new Queue("t2.probe.auditoria." + suffix, false, true, true);
    try {
      admin.declareQueue(q1);
      admin.declareQueue(q2);
      admin.declareBinding(
          BindingBuilder.bind(q1).to(cfg.hospitalExchange()).with("cita.confirmada.v1"));
      admin.declareBinding(
          BindingBuilder.bind(q2).to(cfg.hospitalExchange()).with("cita.confirmada.v1"));
      var rabbit = cfg.eventTemplate(cf, cfg.eventConverter());
      var event =
          new HospitalEvent(
              UUID.randomUUID(),
              "CitaConfirmada",
              1,
              OffsetDateTime.now(ZoneOffset.UTC),
              UUID.randomUUID(),
              UUID.randomUUID(),
              "PENDIENTE",
              "CONFIRMADA",
              "citas");
      rabbit.invoke(
          ops -> {
            ops.convertAndSend(MessagingConfiguration.EXCHANGE, "cita.confirmada.v1", event);
            ops.waitForConfirmsOrDie(3000);
            return true;
          });
      assertThat(rabbit.receiveAndConvert(q1.getName(), 5000))
          .isInstanceOf(HospitalEvent.class)
          .extracting("eventId")
          .isEqualTo(event.eventId());
      assertThat(rabbit.receiveAndConvert(q2.getName(), 5000))
          .isInstanceOf(HospitalEvent.class)
          .extracting("eventId")
          .isEqualTo(event.eventId());
    } finally {
      admin.deleteQueue(q1.getName());
      admin.deleteQueue(q2.getName());
      cf.destroy();
    }
  }
}
