package pe.vidasalud.citas;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CitaInput(
    @NotNull UUID pacienteId,
    @NotBlank @Size(max = 80) String especialidad,
    @NotNull OffsetDateTime fechaHora,
    @NotBlank @Size(max = 50) String profesionalCodigo,
    @NotBlank @Size(max = 200) String motivo) {}
