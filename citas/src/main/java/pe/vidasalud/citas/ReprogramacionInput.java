package pe.vidasalud.citas;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record ReprogramacionInput(
    @NotNull OffsetDateTime fechaHora, @NotBlank @Size(max = 50) String profesionalCodigo) {}
