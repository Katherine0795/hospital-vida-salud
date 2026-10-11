package pe.vidasalud.atenciones;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Atencion(
    UUID id, UUID pacienteId, UUID citaId, OffsetDateTime fecha, String area, String estado) {}
