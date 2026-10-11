package pe.vidasalud.atenciones;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/atenciones")
public class AtencionController {
  private final AtencionService s;

  public AtencionController(AtencionService s) {
    this.s = s;
  }

  @PostMapping
  Mono<ResponseEntity<Atencion>> create(@Valid @RequestBody AtencionInput i) {
    return s.create(i)
        .map(a -> ResponseEntity.created(URI.create("/api/atenciones/" + a.id())).body(a));
  }

  @GetMapping("/{id}")
  Mono<Atencion> get(@PathVariable UUID id) {
    return s.get(id);
  }
}
