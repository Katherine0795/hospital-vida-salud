package pe.vidasalud.citas;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CitaRulesTest {
  Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void rejectsPast() {
    assertThatThrownBy(() -> CitaRules.agenda(OffsetDateTime.parse("2025-01-01T00:00:00Z"), clock))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void rejectsIrregularAgenda() {
    assertThatThrownBy(() -> CitaRules.agenda(OffsetDateTime.parse("2027-01-01T00:11:00Z"), clock))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void acceptsFutureSlot() {
    assertThatCode(() -> CitaRules.agenda(OffsetDateTime.parse("2027-01-01T00:30:00Z"), clock))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CANCELADA", "ATENDIDA", "AUSENTE"})
  void terminalStatesCannotBeConfirmed(String old) {
    assertThatThrownBy(() -> CitaRules.transition(old, "CONFIRMADA", null))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void cannotAttendPending() {
    assertThatThrownBy(() -> CitaRules.transition("PENDIENTE", "ATENDIDA", UUID.randomUUID()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void attendedRequiresReference() {
    assertThatThrownBy(() -> CitaRules.transition("CONFIRMADA", "ATENDIDA", null))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void allowsConfirmation() {
    assertThatCode(() -> CitaRules.transition("PENDIENTE", "CONFIRMADA", null))
        .doesNotThrowAnyException();
  }

  @Test
  void rescheduleRejectsFinalState() {
    assertThatThrownBy(() -> CitaRules.reschedule("ATENDIDA"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void rescheduleAllowsConfirmed() {
    assertThatCode(() -> CitaRules.reschedule("CONFIRMADA")).doesNotThrowAnyException();
  }
}
