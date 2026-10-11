package pe.vidasalud.citas;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;

@RestController
@RequestMapping("/api/citas")
public class CitaController {
  private final CitaService s;

  public CitaController(CitaService s) {
    this.s = s;
  }

  @PostMapping
  Mono<ResponseEntity<Cita>> create(@Valid @RequestBody CitaInput i) {
    return s.create(i).map(c -> ResponseEntity.created(URI.create("/api/citas/" + c.id())).body(c));
  }

  @GetMapping
  Flux<Cita> list() {
    return s.list();
  }

  @GetMapping("/{id}")
  Mono<Cita> get(@PathVariable UUID id) {
    return s.get(id);
  }

  @PatchMapping("/{id}/estado")
  Mono<Cita> change(@PathVariable UUID id, @Valid @RequestBody EstadoInput i) {
    return s.change(id, i);
  }

  @PatchMapping("/{id}/agenda")
  Mono<Cita> reschedule(@PathVariable UUID id, @Valid @RequestBody ReprogramacionInput input) {
    return s.reschedule(id, input);
  }
}
