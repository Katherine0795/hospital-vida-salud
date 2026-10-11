package pe.vidasalud.pacientes;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;

@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {
  private final PacienteService s;

  public PacienteController(PacienteService s) {
    this.s = s;
  }

  @PostMapping
  Mono<ResponseEntity<Paciente>> create(@Valid @RequestBody PacienteInput i) {
    return s.create(i)
        .map(p -> ResponseEntity.created(URI.create("/api/pacientes/" + p.id())).body(p));
  }

  @GetMapping
  Flux<Paciente> list() {
    return s.list();
  }

  @GetMapping("/{id}")
  Mono<Paciente> get(@PathVariable UUID id) {
    return s.get(id);
  }

  @PutMapping("/{id}")
  Mono<Paciente> update(@PathVariable UUID id, @Valid @RequestBody PacienteInput i) {
    return s.update(id, i);
  }
}
