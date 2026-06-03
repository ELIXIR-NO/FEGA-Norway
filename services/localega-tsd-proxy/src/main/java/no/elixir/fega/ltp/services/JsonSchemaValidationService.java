package no.elixir.fega.ltp.services;

import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.networknt.schema.regex.GraalJSRegularExpressionFactory;
import java.util.stream.Collectors;
import no.elixir.fega.ltp.exceptions.JsonSchemaValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JsonSchemaValidationService {

  private final Schema schema;

  public JsonSchemaValidationService(
      @Value("${json.schema.export-request.location:classpath:export-request.json}")
          String schemaLocation) {
    try {
      SchemaRegistryConfig schemaRegistryConfig =
          SchemaRegistryConfig.builder()
              .regularExpressionFactory(GraalJSRegularExpressionFactory.getInstance())
              .build();

      SchemaRegistry schemaRegistry =
          SchemaRegistry.withDefaultDialect(
              SpecificationVersion.DRAFT_7,
              builder -> builder.schemaRegistryConfig(schemaRegistryConfig));

      this.schema = schemaRegistry.getSchema(SchemaLocation.of(schemaLocation));
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to load JSON schema from location '" + schemaLocation + "'", e);
    }
  }

  public void validate(String jsonContent) throws JsonSchemaValidationException {
    var errors =
        schema.validate(
            jsonContent,
            InputFormat.JSON,
            ctx -> ctx.executionConfig(c -> c.formatAssertionsEnabled(true)));

    if (!errors.isEmpty()) {
      String errorMessage =
          errors.stream().map(Error::getMessage).collect(Collectors.joining("\n"));
      throw new JsonSchemaValidationException("JSON Schema Validation Failed: " + errorMessage);
    }
  }
}
