package pe.vidasalud.atenciones;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record AtencionInput(@NotNull UUID citaId, @NotBlank @Size(max = 80) String area) {}
