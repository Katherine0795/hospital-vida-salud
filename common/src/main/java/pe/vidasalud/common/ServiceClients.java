package pe.vidasalud.common;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

public class ServiceClients {
  private final WebClient http;

  public ServiceClients(WebClient http) {
    this.http = http;
  }

  public record PatientRef(UUID id, String estado) {}

  public record AtencionRef(UUID id, UUID pacienteId, UUID citaId, String estado) {}

  public Mono<AtencionRef> attention(UUID id) {
    return handle(
        http.get()
            .uri("http://atenciones/api/atenciones/{id}", id)
            .retrieve()
            .bodyToMono(AtencionRef.class));
  }

  public record CitaRef(UUID id, UUID pacienteId, String estado, UUID atencionId) {}

  private <T> Mono<T> handle(Mono<T> call) {
    return call.timeout(Duration.ofSeconds(2))
        .onErrorMap(
            e -> {
              if (e instanceof WebClientResponseException r) {
                if (r.getStatusCode().value() == 404)
                  return new ResponseStatusException(
                      HttpStatus.NOT_FOUND, "Referencia inexistente");
                if (r.getStatusCode().is4xxClientError())
                  return new ResponseStatusException(
                      HttpStatus.CONFLICT, "Referencia o estado incompatible");
                return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Servicio dependiente no disponible");
              }
              if (e instanceof TimeoutException)
                return new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT, "Tiempo de espera agotado");
              if (e instanceof WebClientRequestException)
                return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Servicio dependiente no disponible");
              return e;
            });
  }

  public Mono<PatientRef> patient(UUID id) {
    return handle(
        http.get()
            .uri("http://pacientes/api/pacientes/{id}", id)
            .retrieve()
            .bodyToMono(PatientRef.class));
  }

  public Mono<CitaRef> cita(UUID id) {
    return handle(
        http.get().uri("http://citas/api/citas/{id}", id).retrieve().bodyToMono(CitaRef.class));
  }

  public Mono<CitaRef> closeCita(UUID id, UUID atencionId) {
    return handle(
        http.patch()
            .uri("http://citas/api/citas/{id}/estado", id)
            .bodyValue(java.util.Map.of("estado", "ATENDIDA", "atencionId", atencionId))
            .retrieve()
            .bodyToMono(CitaRef.class));
  }
}
