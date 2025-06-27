package no.elixir.fega.ltp.aspects;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

/** AOP aspect that processes HTTP request parameters. */
@Slf4j
@Aspect
@Order(2)
@Component
public class ProcessArgumentsAspect {

  public static final String EGA_USERNAME = "egaUsername";
  public static final String ELIXIR_ID = "elixirId";
  public static final String FILE_NAME = "fileName";
  public static final String UPLOAD_ID = "uploadId";
  public static final String CHUNK = "chunk";
  public static final String FILE_SIZE = "fileSize";
  public static final String MD5 = "md5";
  public static final String SHA256 = "sha256";

  @Autowired private HttpServletRequest request;

  /**
   * Converts HTTP request parameters to request attributes.
   *
   * @param joinPoint Join point referencing proxied method.
   * @return Either the object, returned by the proxied method, or HTTP error response.
   * @throws Throwable In case of error.
   */
  @SuppressWarnings("rawtypes")
  @Around(
      "execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.stream(..)) || execution(public * no.elixir.fega.ltp.controllers.rest.ProxyController.deleteFile(..))")
  public Object processArguments(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
      Object[] arguments = joinPoint.getArgs();
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      String[] parameterNames = signature.getParameterNames();
      Class[] parameterTypes = signature.getParameterTypes();

      Object elixirId = request.getAttribute(ELIXIR_ID);
      log.info("ELIXIR_ID attribute: {}", elixirId);

      log.info("Method parameters");
        for (int i = 0; i < arguments.length; i++) {
          log.info("{} ({}): {}", parameterNames[i], parameterTypes[i].getSimpleName(), arguments[i]);
        }

      log.info("HTTP parameters");
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
          log.info("{} -> {}", e.getKey(), Arrays.toString(e.getValue()));
        }

      for (int i = 0; i < arguments.length; i++) {
        if (parameterTypes[i].equals(String.class)) {
          switch (parameterNames[i]) {
            case FILE_NAME:
              request.setAttribute(FILE_NAME, arguments[i]);
              break;
            case UPLOAD_ID:
              request.setAttribute(UPLOAD_ID, arguments[i]);
              break;
            case CHUNK:
              request.setAttribute(CHUNK, arguments[i]);
              break;
            case FILE_SIZE:
              request.setAttribute(FILE_SIZE, arguments[i]);
              break;
            case MD5:
              request.setAttribute(MD5, arguments[i]);
              break;
            case SHA256:
              request.setAttribute(SHA256, arguments[i]);
              break;
          }
        }
      }
      return joinPoint.proceed(arguments);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }
  }
}
