package it.boff.bike;

import it.boff.bike.accesso.CodiceAccessoService;
import it.boff.bike.accesso.PasskeyService;
import it.boff.bike.privacy.PrivacyService;
import it.boff.bike.security.AccessoPerRuolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import it.boff.bike.utente.UtenteService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Fase 11: accesso con codice via email, passkey, durata dell'accesso per ruolo. */
@IntegrationTest
class AccessoIT {

    @Autowired WebApplicationContext context;
    @Autowired CodiceAccessoService codici;
    @Autowired UtenteService utenteService;
    @Autowired UtenteRepository utenti;
    @Autowired PasskeyService passkey;
    @Autowired PublicKeyCredentialUserEntityRepository utentiWebAuthn;
    @Autowired UserCredentialRepository credenziali;
    @Autowired PrivacyService privacy;
    @Autowired JdbcTemplate jdbc;

    MockMvc mvc;

    @BeforeEach
    void prepara() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    Utente nuovoCliente() {
        return utenteService.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email()));
    }

    /** Il codice in chiaro non esce dal servizio: per i test lo si rigenera con un hash noto. */
    String codiceNoto(Utente u) {
        String codice = "4" + String.format("%05d", (int) (Math.random() * 100_000));
        jdbc.update("UPDATE codice_accesso SET tentativi = 5 WHERE utente_id = ?", u.getId());
        jdbc.update("INSERT INTO codice_accesso (utente_id, hash, scade_il, creato_il) VALUES (?, ?, now() + interval '10 minutes', now() - interval '1 minute')",
                u.getId(), hashDi(u.getId(), codice));
        return codice;
    }

    static String hashDi(Long id, String codice) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest((id + ":" + codice).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("username", email).param("password", password)).andReturn();
    }

    static boolean riuscito(MvcResult r) {
        String dove = r.getResponse().getRedirectedUrl();
        return dove != null && !dove.contains("error");
    }

    @Test
    void codiceGiustoUnaVoltaSola() throws Exception {
        Utente u = nuovoCliente();
        String codice = codiceNoto(u);
        MvcResult ok = login(u.getEmail(), codice);
        assertThat(riuscito(ok)).isTrue();
        Cookie resta = ok.getResponse().getCookie("remember-me");
        assertThat(resta).isNotNull();
        assertThat(resta.getMaxAge()).as("clienti: 30 giorni").isEqualTo((int) AccessoPerRuolo.DURATA_CLIENTI.toSeconds());

        assertThat(riuscito(login(u.getEmail(), codice))).as("non si può riusare").isFalse();
        // la password continua a funzionare
        assertThat(riuscito(login(u.getEmail(), "password1"))).isTrue();
    }

    @Test
    void codiceSbagliatoBruciatoDopoCinqueTentativi() throws Exception {
        Utente u = nuovoCliente();
        String codice = codiceNoto(u);
        String sbagliato = codice.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < CodiceAccessoService.MAX_TENTATIVI; i++) {
            assertThat(codici.verifica(u.getEmail(), sbagliato)).isFalse();
        }
        assertThat(codici.verifica(u.getEmail(), codice)).as("dopo 5 errori il codice non vale più").isFalse();
    }

    @Test
    void codiceDiUnAltroUtenteOScadutoNonVale() {
        Utente a = nuovoCliente(), b = nuovoCliente();
        String codiceA = codiceNoto(a);
        assertThat(codici.verifica(b.getEmail(), codiceA)).isFalse();
        jdbc.update("UPDATE codice_accesso SET scade_il = now() - interval '1 minute' WHERE utente_id = ?", a.getId());
        assertThat(codici.verifica(a.getEmail(), codiceA)).isFalse();
    }

    @Test
    void nuovoCodiceAnnullaIlPrecedenteELeRichiesteSonoLimitate() {
        Utente u = nuovoCliente();
        String vecchio = codiceNoto(u);
        codici.richiedi(u.getEmail()); // vero invio: annulla il precedente
        assertThat(codici.verifica(u.getEmail(), vecchio)).isFalse();
        Integer prima = jdbc.queryForObject("SELECT count(*) FROM codice_accesso WHERE utente_id = ?", Integer.class, u.getId());
        codici.richiedi(u.getEmail()); // troppo presto: ignorato in silenzio
        Integer dopo = jdbc.queryForObject("SELECT count(*) FROM codice_accesso WHERE utente_id = ?", Integer.class, u.getId());
        assertThat(dopo).isEqualTo(prima);
        codici.richiedi("nessuno-" + System.nanoTime() + "@example.com"); // email sconosciuta: nessun errore
    }

    @Test
    void accountDisattivatoNonEntraColCodice() throws Exception {
        Utente u = nuovoCliente();
        String codice = codiceNoto(u);
        utenteService.impostaAttivo(u.getId(), false);
        assertThat(riuscito(login(u.getEmail(), codice))).isFalse();
    }

    @Test
    void ilPersonaleRestaCollegatoDodiciOre() throws Exception {
        Utente staff = utenti.findByEmail("collaboratore@demo.it").orElseThrow();
        MvcResult ok = login(staff.getEmail(), codiceNoto(staff));
        assertThat(riuscito(ok)).isTrue();
        assertThat(ok.getResponse().getCookie("remember-me").getMaxAge())
                .isEqualTo((int) AccessoPerRuolo.DURATA_PERSONALE.toSeconds());
    }

    @Test
    void passkeyElencateRimosseSoloDalProprietarioECancellateConIDati() {
        Utente u = nuovoCliente(), altro = nuovoCliente();
        String id = aggiungiPasskey(u, "iPhone");
        aggiungiPasskey(altro, "Mac");
        assertThat(passkey.elenco(u.getEmail())).extracting(PasskeyService.Passkey::nome).containsExactly("iPhone");

        passkey.rimuovi(altro.getEmail(), id); // non sua: nessun effetto
        assertThat(passkey.elenco(u.getEmail())).hasSize(1);
        passkey.rimuovi(u.getEmail(), id);
        assertThat(passkey.elenco(u.getEmail())).isEmpty();

        aggiungiPasskey(u, "iPad");
        codiceNoto(u);
        String email = u.getEmail();
        privacy.eliminaAccount(u.getId());
        assertThat(utentiWebAuthn.findByUsername(email)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codice_accesso WHERE utente_id = ?", Integer.class, u.getId()))
                .isZero();
        assertThat(passkey.elenco(altro.getEmail())).hasSize(1);
    }

    String aggiungiPasskey(Utente u, String nome) {
        var entita = utentiWebAuthn.findByUsername(u.getEmail());
        if (entita == null) {
            entita = ImmutablePublicKeyCredentialUserEntity.builder()
                    .id(Bytes.random()).name(u.getEmail()).displayName(u.getNomeCompleto()).build();
            utentiWebAuthn.save(entita);
        }
        Bytes id = Bytes.random();
        credenziali.save(ImmutableCredentialRecord.builder()
                .credentialId(id).userEntityUserId(entita.getId()).label(nome)
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3}))
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .signatureCount(0).uvInitialized(true).backupEligible(true).backupState(true)
                .transports(Set.of()).created(Instant.now()).lastUsed(Instant.now())
                .build());
        return id.toBase64UrlString();
    }
}
