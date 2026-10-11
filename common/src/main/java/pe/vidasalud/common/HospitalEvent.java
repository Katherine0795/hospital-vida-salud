package pe.vidasalud.common;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HospitalEvent(
    UUID eventId,
    String eventType,
    int version,
    OffsetDateTime occurredAt,
    UUID citaId,
    UUID pacienteId,
    String estadoAnterior,
    String estadoNuevo,
    String origen) {}
