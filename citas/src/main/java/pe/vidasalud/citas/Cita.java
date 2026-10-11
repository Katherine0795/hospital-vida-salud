package pe.vidasalud.citas;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Cita(
    UUID id,
    UUID pacienteId,
    String especialidad,
    OffsetDateTime fechaHora,
    String profesionalCodigo,
    String motivo,
    String estado,
    OffsetDateTime fechaCreacion,
    OffsetDateTime fechaActualizacion,
    int version,
    UUID atencionId) {}
