package pe.vidasalud.atenciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Import({
  pe.vidasalud.common.HospitalConfiguration.class,
  pe.vidasalud.common.Errors.class
})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
