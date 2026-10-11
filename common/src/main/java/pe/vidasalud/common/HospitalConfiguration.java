package pe.vidasalud.common;

import java.time.Clock;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HospitalConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  @LoadBalanced
  WebClient.Builder loadBalancedWebClient() {
    return WebClient.builder();
  }

  @Bean
  ServiceClients serviceClients(WebClient.Builder b) {
    return new ServiceClients(b.build());
  }
}
