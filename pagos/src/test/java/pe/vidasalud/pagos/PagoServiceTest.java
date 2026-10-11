package pe.vidasalud.pagos;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PagoServiceTest {
  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "0.001", "10000000000"})
  void invalidAmount(String v) {
    assertThatThrownBy(() -> PagoService.validAmount(new BigDecimal(v)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void positiveIsAllowed() {
    assertThatCode(() -> PagoService.validAmount(new BigDecimal("0.00")))
        .doesNotThrowAnyException();
  }
}
