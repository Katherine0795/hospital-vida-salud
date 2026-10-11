package pe.vidasalud.pacientes;

import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pe.vidasalud.common.Errors;
import reactor.core.publisher.*;

@Service
public class PacienteService {
  private final PacienteRepository repo;
  private final Clock clock;

  public PacienteService(PacienteRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  public Mono<Paciente> get(UUID id) {
    return repo.find(id).switchIfEmpty(Mono.error(Errors.missing()));
  }

  public Flux<Paciente> list() {
    return repo.list();
  }

  private Paciente build(UUID id, PacienteInput i, OffsetDateTime date) {
    return new Paciente(
        id,
        i.tipoDocumento(),
        i.numeroDocumento().trim(),
        i.nombres().trim(),
        i.apellidos().trim(),
        i.fechaNacimiento(),
        i.telefono(),
        i.correo(),
        i.direccion(),
        date,
        i.estado() == null ? "ACTIVO" : i.estado());
  }

  public Mono<Paciente> create(PacienteInput i) {
    return Mono.defer(() -> repo.insert(build(UUID.randomUUID(), i, OffsetDateTime.now(clock))));
  }

  public Mono<Paciente> update(UUID id, PacienteInput i) {
    return get(id).flatMap(old -> repo.update(build(id, i, old.fechaRegistro())));
  }
}
