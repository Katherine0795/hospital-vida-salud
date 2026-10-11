package pe.vidasalud.atenciones;

import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import pe.vidasalud.common.*;
import reactor.core.publisher.Mono;

@Service
public class AtencionService {
  private final AtencionRepository repo;
  private final ServiceClients clients;
  private final Clock clock;

  public AtencionService(AtencionRepository repo, ServiceClients clients, Clock clock) {
    this.repo = repo;
    this.clients = clients;
    this.clock = clock;
  }

  public Mono<Atencion> get(UUID id) {
    return repo.find(id).switchIfEmpty(Mono.error(Errors.missing()));
  }

  private Mono<Atencion> finish(Atencion a) {
    if (a.estado().equals("REGISTRADA")) return Mono.just(a);
    return clients.closeCita(a.citaId(), a.id()).then(repo.complete(a.id()));
  }

  public Mono<Atencion> create(AtencionInput i) {
    return repo.byCita(i.citaId())
        .switchIfEmpty(
            Mono.defer(
                () ->
                    clients
                        .cita(i.citaId())
                        .flatMap(
                            c -> {
                              if (!"CONFIRMADA".equals(c.estado()))
                                return Mono.error(Errors.conflict("La cita debe estar confirmada"));
                              var a =
                                  new Atencion(
                                      UUID.randomUUID(),
                                      c.pacienteId(),
                                      c.id(),
                                      OffsetDateTime.now(clock),
                                      i.area(),
                                      "PENDIENTE_CIERRE");
                              return repo.insert(a)
                                  .onErrorResume(
                                      DuplicateKeyException.class, e -> repo.byCita(i.citaId()));
                            })))
        .flatMap(this::finish);
  }
}
