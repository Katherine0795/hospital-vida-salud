package pe.vidasalud.pagos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Pago(
    UUID id,
    UUID pacienteId,
    String concepto,
    BigDecimal monto,
    OffsetDateTime fecha,
    String medio,
    String numeroOperacion,
    String estado) {}
