package pe.vidasalud.pagos;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record PagoInput(
    @NotNull UUID pacienteId,
    @NotBlank @Size(max = 150) String concepto,
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2)
        BigDecimal monto,
    @NotBlank @Pattern(regexp = "EFECTIVO|TARJETA|TRANSFERENCIA") String medio,
    @NotBlank @Size(max = 50) String numeroOperacion) {}
