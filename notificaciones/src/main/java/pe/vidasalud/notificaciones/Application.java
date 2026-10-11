package pe.vidasalud.notificaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.context.annotation.Import({
  pe.vidasalud.common.HospitalConfiguration.class,
  pe.vidasalud.common.Errors.class,
  pe.vidasalud.common.MessagingConfiguration.class
})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
