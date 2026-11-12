package no.elixir.fega.ltp.aspects;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exempts a {@code ProxyController} endpoint from the EGA credentials check. Every endpoint needs
 * both the LS AAI token and the EGA credentials unless it carries this, so a new endpoint cannot
 * ship without EGA auth by forgetting an annotation.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoEgaAuth {}
