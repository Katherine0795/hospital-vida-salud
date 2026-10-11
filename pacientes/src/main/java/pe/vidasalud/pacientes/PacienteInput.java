package pe.vidasalud.pacientes;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PacienteInput(
    @NotBlank @Pattern(regexp = "DNI|OTRO") String tipoDocumento,
    @NotBlank @Size(max = 30) String numeroDocumento,
    @NotBlank @Size(max = 100) String nombres,
    @NotBlank @Size(max = 100) String apellidos,
    @NotNull @Past LocalDate fechaNacimiento,
    @NotBlank @Size(max = 30) String telefono,
    @NotBlank @Email @Size(max = 150) String correo,
    @Size(max = 250) String direccion,
    @Pattern(regexp = "ACTIVO|INACTIVO") String estado) {}
