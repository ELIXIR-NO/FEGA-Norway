package no.elixir.fega.ltp.config;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.wiremock.spring.EnableWireMock;

@SpringBootTest
@EnableWireMock
public class JwtDecoderConfigTest {

  @Test
  void shouldOnlyCallOidcOnceWhenCacheIsWorking(WireMockRuntimeInfo wmRuntimeInfo) {
    stubFor(
        get(urlEqualTo("/oidc/jwk"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"keys\":[]}")));

    JwtDecoderConfig config =
        new JwtDecoderConfig(10, 1, TimeUnit.MINUTES, wmRuntimeInfo.getHttpBaseUrl());

    JwtDecoder decoder = config.jwtDecoder();

    try {
      decoder.decode("some.mock.jwt");
    } catch (Exception e) {
      /* ignore validation error */
    }
    try {
      decoder.decode("some.mock.jwt");
    } catch (Exception e) {
      /* ignore validation error */
    }

    verify(1, getRequestedFor(urlEqualTo("/oidc/jwk")));
  }

  @Test
  void shouldRefetchAfterCacheExpires(WireMockRuntimeInfo wmRuntimeInfo)
      throws InterruptedException {
    JwtDecoderConfig config =
        new JwtDecoderConfig(10, 100, TimeUnit.MILLISECONDS, wmRuntimeInfo.getHttpBaseUrl());

    JwtDecoder decoder = config.jwtDecoder();

    try {
      decoder.decode("jwt1");
    } catch (Exception e) {
      /* ignore validation error */
    }

    Thread.sleep(200);

    try {
      decoder.decode("jwt1");
    } catch (Exception e) {
      /* ignore validation error */
    }

    verify(2, getRequestedFor(urlEqualTo("/oidc/jwk")));
  }
}
