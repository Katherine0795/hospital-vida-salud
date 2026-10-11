package pe.vidasalud.citas;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record EstadoInput(
    @NotBlank @Pattern(regexp = "CONFIRMADA|CANCELADA|ATENDIDA|AUSENTE") String estado,
    UUID atencionId) {}
