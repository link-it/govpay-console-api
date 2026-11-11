package it.govpay.console.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.servlet.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code SameSite} sul cookie CSRF creato al login FORM (issue #128).
 *
 * <p>Il cookie di sessione si configura con
 * {@code server.servlet.session.cookie.same-site}; il cookie CSRF lo scrive il
 * {@code CookieCsrfTokenRepository} di govpay-common-auth, che non lo
 * imposta. Questo supplier gli applica lo stesso valore del cookie di sessione,
 * cosi' i due restano allineati.
 */
@Configuration(proxyBeanMethods = false)
public class CookieSameSiteConfig {

    @Bean
    CookieSameSiteSupplier csrfCookieSameSiteSupplier(
            @Value("${server.servlet.session.cookie.same-site:lax}") SameSite sameSite,
            @Value("${govpay.auth.form.csrf-cookie-name:XSRF-TOKEN}") String csrfCookieName) {
        return CookieSameSiteSupplier.of(sameSite).whenHasName(csrfCookieName);
    }
}
