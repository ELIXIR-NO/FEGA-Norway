package no.elixir.tsdapimock;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootApplication
public class TsdApiMockApplication {
  public static void main(String[] args) {
    SpringApplication.run(TsdApiMockApplication.class, args);
  }

  @Bean
  public CommandLineRunner logEndpoints(RequestMappingHandlerMapping mapping) {
    return args -> {
      System.out.println("\n=== Registered Endpoints ===");
      mapping
          .getHandlerMethods()
          .forEach(
              (key, value) -> {
                System.out.printf(
                    "%s -> %s#%s%n",
                    key,
                    value.getMethod().getDeclaringClass().getSimpleName(),
                    value.getMethod().getName());
              });
      System.out.println("============================\n");
    };
  }
}
