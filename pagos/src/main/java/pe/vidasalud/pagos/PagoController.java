package pe.vidasalud.pagos;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {
  private final PagoService s;

  public PagoController(PagoService s) {
    this.s = s;
  }

  @PostMapping
  Mono<ResponseEntity<Pago>> create(@Valid @RequestBody PagoInput i) {
    return s.create(i).map(p -> ResponseEntity.created(URI.create("/api/pagos/" + p.id())).body(p));
  }

  @GetMapping("/{id}")
  Mono<Pago> get(@PathVariable UUID id) {
    return s.get(id);
  }
}
