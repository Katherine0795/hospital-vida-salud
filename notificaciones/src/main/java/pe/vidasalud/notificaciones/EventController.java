package pe.vidasalud.notificaciones;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import pe.vidasalud.common.HospitalEvent;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/notificaciones")
public class EventController {
  private final EventRepository repo;

  public EventController(EventRepository r) {
    repo = r;
  }

  @GetMapping
  Flux<HospitalEvent> list(@RequestParam(name = "citaId") UUID id) {
    return repo.list(id);
  }
}
