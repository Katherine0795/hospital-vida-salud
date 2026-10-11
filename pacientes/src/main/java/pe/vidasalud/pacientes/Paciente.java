package pe.vidasalud.pacientes;

import java.time.*;
import java.util.UUID;

public record Paciente(
    UUID id,
    String tipoDocumento,
    String numeroDocumento,
    String nombres,
    String apellidos,
    LocalDate fechaNacimiento,
    String telefono,
    String correo,
    String direccion,
    OffsetDateTime fechaRegistro,
    String estado) {}
