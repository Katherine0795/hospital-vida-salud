package pe.vidasalud.pagos;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.vidasalud.common.*;
import reactor.core.publisher.Mono;

@Service
public class PagoService {
  private final PagoRepository repo;
  private final ServiceClients clients;
  private final Clock clock;

  public PagoService(PagoRepository repo, ServiceClients clients, Clock clock) {
    this.repo = repo;
    this.clients = clients;
    this.clock = clock;
  }

  public static void validAmount(BigDecimal amount) {
    if (amount == null
        || amount.signum() <= 0
        || amount.scale() > 2
        || amount.precision() - amount.scale() > 10) throw Errors.invalid("Monto de pago invalido");
  }

  public Mono<Pago> create(PagoInput i) {
    return Mono.defer(
        () -> {
          validAmount(i.monto());
          return clients
              .patient(i.pacienteId())
              .flatMap(
                  p ->
                      repo.insert(
                          new Pago(
                              UUID.randomUUID(),
                              p.id(),
                              i.concepto(),
                              i.monto(),
                              OffsetDateTime.now(clock),
                              i.medio(),
                              i.numeroOperacion(),
                              "REGISTRADO")));
        });
  }

  public Mono<Pago> get(UUID id) {
    return repo.find(id).switchIfEmpty(Mono.error(Errors.missing()));
  }
}
