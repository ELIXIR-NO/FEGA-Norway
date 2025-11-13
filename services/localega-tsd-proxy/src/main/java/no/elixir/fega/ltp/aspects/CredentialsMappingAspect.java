package no.elixir.fega.ltp.aspects;

import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.EGA_USERNAME;
import static no.elixir.fega.ltp.aspects.ProcessArgumentsAspect.ELIXIR_ID;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import no.elixir.fega.ltp.common.Masker;
import org.apache.commons.collections4.CollectionUtils;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** AOP aspect that maps EGA username with Elixir ID. */
@Slf4j
@Aspect
@Order(3)
@Component
public class CredentialsMappingAspect {

  private final HttpServletRequest request;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  CredentialsMappingAspect(HttpServletRequest request, JdbcTemplate jdbcTemplate) {
    this.request = request;
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Stores EGA username to Elixir ID mapping.
   *
   * @param result Object returned by the proxied method.
   */
  @AfterReturning(
      pointcut =
          "execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.stream(..))",
      returning = "result")
  public void publishMessage(Object result) {
    String egaUsername = request.getAttribute(EGA_USERNAME).toString();
    String elixirId = request.getAttribute(ELIXIR_ID).toString();
    List<String> existingEntries =
        jdbcTemplate.queryForList(
            "select elixir_id from mapping where ega_id = ?", String.class, egaUsername);
    if (CollectionUtils.isNotEmpty(existingEntries)) {
      log.info("EGA account [{}] is already mapped to Elixir account [{}]", Masker.maskUsername(egaUsername), Masker.maskEmail(elixirId));
      return;
    }
    jdbcTemplate.update("insert into mapping values (?, ?)", egaUsername, elixirId);
    log.info("Mapped EGA account [{}] to Elixir account [{}]", Masker.maskUsername(egaUsername), Masker.maskEmail(elixirId));
  }
}
