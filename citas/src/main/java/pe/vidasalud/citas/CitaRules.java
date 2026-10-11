package pe.vidasalud.citas;

import java.time.*;
import java.util.*;
import pe.vidasalud.common.Errors;

public class CitaRules {
  public static void agenda(OffsetDateTime date, Clock clock) {
    if (!date.toInstant().isAfter(clock.instant())) throw Errors.invalid("La cita debe ser futura");
    var local = date.atZoneSameInstant(ZoneId.of("America/Lima"));
    if (local.getMinute() % 30 != 0 || local.getSecond() != 0 || local.getNano() != 0)
      throw Errors.invalid("Agenda de bloques de 30 minutos");
  }

  public static void transition(String old, String next, UUID atencionId) {
    boolean ok =
        switch (old) {
          case "PENDIENTE" -> Set.of("CONFIRMADA", "CANCELADA").contains(next);
          case "CONFIRMADA" -> Set.of("ATENDIDA", "CANCELADA", "AUSENTE").contains(next);
          default -> false;
        };
    if (!ok) throw Errors.conflict("Transicion de cita no permitida");
    if (next.equals("ATENDIDA") && atencionId == null)
      throw Errors.invalid("ATENDIDA requiere referencia de atencion");
  }

  public static void reschedule(String state) {
    if (!Set.of("PENDIENTE", "CONFIRMADA").contains(state))
      throw Errors.conflict("Estado no permite reprogramar");
  }
}
