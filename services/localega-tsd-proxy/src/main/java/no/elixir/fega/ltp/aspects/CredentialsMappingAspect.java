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
  public void mapCredentials(Object result) {
    Object egaUsernameAttr = request.getAttribute(EGA_USERNAME);
    Object elixirIdAttr = request.getAttribute(ELIXIR_ID);
    if (egaUsernameAttr == null || elixirIdAttr == null) {
      log.error(
          "Missing required request attributes: EGA_USERNAME={}, ELIXIR_ID={}",
          egaUsernameAttr, elixirIdAttr);
      return;
    }
    String egaUsername = egaUsernameAttr.toString();
    String elixirId = elixirIdAttr.toString();
    List<String> existingEntries =
        jdbcTemplate.queryForList(
            "select elixir_id from mapping where ega_id = ?", String.class, egaUsername);
    if (CollectionUtils.isEmpty(existingEntries)) {
      jdbcTemplate.update("insert into mapping values (?, ?)", egaUsername, elixirId);
      log.info(
          "Mapped EGA account [{}] to Elixir account [{}]",
          Masker.maskUsername(egaUsername),
          Masker.maskEmail(elixirId));
      return;
    }
    if (existingEntries.getFirst().equals(elixirId)) {
      log.info(
          "EGA account [{}] is already mapped to Elixir account [{}]",
          Masker.maskUsername(egaUsername),
          Masker.maskEmail(elixirId));
    } else {
      log.info(
          "EGA account [{}] has a different Elixir account [{}]",
          Masker.maskUsername(egaUsername),
          Masker.maskEmail(elixirId));
      throw new IllegalStateException(
          "EGA account is already mapped to a different Elixir account");
    }
  }
}
