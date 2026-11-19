package it.boff.bike.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ScadenzaAccessoPersonaleTest {

    static final Instant ORA = Instant.parse("2026-11-19T08:00:00Z");

    @AfterEach
    void pulisci() {
        SecurityContextHolder.clearContext();
    }

    static MockHttpServletResponse richiesta(MockHttpSession sessione, Instant quando, String ruolo) throws Exception {
        return richiesta(sessione, quando, ruolo, false);
    }

    static MockHttpServletResponse richiesta(MockHttpSession sessione, Instant quando, String ruolo, boolean vaadin)
            throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "x@y.it", null, AuthorityUtils.createAuthorityList("ROLE_" + ruolo)));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/bacheca");
        req.setSession(sessione);
        if (vaadin) req.setParameter("v-r", "uidl");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new ScadenzaAccessoPersonale(Clock.fixed(quando, ZoneOffset.UTC)).doFilter(req, resp, new MockFilterChain());
        return resp;
    }

    @Test
    void ilPersonaleVieneDisconnessoDopoDodiciOre() throws Exception {
        MockHttpSession sessione = new MockHttpSession();
        assertThat(richiesta(sessione, ORA, "STAFF").getRedirectedUrl()).isNull();
        assertThat(richiesta(sessione, ORA.plus(Duration.ofHours(11)), "STAFF").getRedirectedUrl()).isNull();
        assertThat(richiesta(sessione, ORA.plus(Duration.ofHours(12)).plusSeconds(1), "STAFF").getRedirectedUrl())
                .isEqualTo("/login");
        assertThat(sessione.isInvalid()).isTrue();
    }

    @Test
    void iClientiNonHannoQuestoLimite() throws Exception {
        MockHttpSession sessione = new MockHttpSession();
        richiesta(sessione, ORA, "CLIENTE");
        assertThat(richiesta(sessione, ORA.plus(Duration.ofDays(2)), "CLIENTE").getRedirectedUrl()).isNull();
        assertThat(sessione.isInvalid()).isFalse();
    }

    @Test
    void unaRichiestaInternaDiVaadinNonVieneRediretta() throws Exception {
        MockHttpSession sessione = new MockHttpSession();
        richiesta(sessione, ORA, "STAFF");
        MockHttpServletResponse r = richiesta(sessione, ORA.plus(Duration.ofHours(13)), "STAFF", true);
        assertThat(r.getRedirectedUrl()).isNull();
        assertThat(sessione.isInvalid()).isTrue();
    }
}
