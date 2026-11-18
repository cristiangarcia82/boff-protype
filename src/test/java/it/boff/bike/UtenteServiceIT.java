package it.boff.bike;

import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.utente.DatiAnagrafica;
import it.boff.bike.utente.DatiRegistrazione;
import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import it.boff.bike.utente.UtenteService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class UtenteServiceIT {

    @Autowired UtenteService service;
    @Autowired UtenteRepository utenti;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CatalogoService catalogo;

    static DatiRegistrazione registrazione(String email) {
        DatiRegistrazione d = new DatiRegistrazione();
        d.setNome("Anna");
        d.setCognome("Neri");
        d.setEmail(email);
        d.setTelefono("+39 333 1234567");
        d.setPassword("password1");
        d.setPrivacyAccettata(true);
        return d;
    }

    static String email() {
        return UUID.randomUUID() + "@Test.it";
    }

    @Test
    void registrazioneCreaUnClienteConPrivacyEPasswordCifrata() {
        Utente u = service.registraCliente(registrazione(email()));
        assertThat(u.getRuolo()).isEqualTo(Ruolo.CLIENTE);
        assertThat(u.isTitolare()).isFalse();
        assertThat(u.getEmail()).endsWith("@test.it");
        assertThat(u.getPrivacyAccettataIl()).isNotNull();
        assertThat(u.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("password1", u.getPasswordHash())).isTrue();
    }

    @Test
    void registrazioneRichiedePrivacyPasswordLungaEmailUnica() {
        DatiRegistrazione senzaPrivacy = registrazione(email());
        senzaPrivacy.setPrivacyAccettata(false);
        assertThatThrownBy(() -> service.registraCliente(senzaPrivacy)).isInstanceOf(ConstraintViolationException.class);

        DatiRegistrazione corta = registrazione(email());
        corta.setPassword("1234567");
        assertThatThrownBy(() -> service.registraCliente(corta)).isInstanceOf(ConstraintViolationException.class);

        String e = email();
        service.registraCliente(registrazione(e));
        assertThatThrownBy(() -> service.registraCliente(registrazione(e.toUpperCase())))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("Esiste già");
    }

    @Test
    void ilNegozioCreaUnClienteSenzaPasswordNota() {
        DatiAnagrafica d = new DatiAnagrafica();
        d.setNome("Pino");
        d.setCognome("Gialli");
        d.setEmail(email());
        d.setTelefono("0471 123456");
        Utente u = service.creaCliente(d);
        assertThat(u.getRuolo()).isEqualTo(Ruolo.CLIENTE);
        assertThat(service.cercaClienti("pino gia")).extracting(Utente::getId).contains(u.getId());
        assertThat(service.cercaClienti("0471 12")).extracting(Utente::getId).contains(u.getId());
    }

    @Test
    void cambioPasswordVerificaQuellaAttuale() {
        Utente u = service.registraCliente(registrazione(email()));
        assertThatThrownBy(() -> service.cambiaPassword(u.getId(), "sbagliata", "nuovapass"))
                .isInstanceOf(ErroreDominio.class);
        service.cambiaPassword(u.getId(), "password1", "nuovapass");
        assertThat(passwordEncoder.matches("nuovapass", utenti.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void ilTitolareNonPuoEssereDisattivato() {
        Utente titolare = utenti.findByEmail("titolare@negozio.it").orElseThrow();
        assertThatThrownBy(() -> service.impostaAttivo(titolare.getId(), false)).isInstanceOf(ErroreDominio.class);
    }

    @Test
    void collaboratoreCreatoComeStaff() {
        Utente u = service.creaCollaboratore(registrazione(email()));
        assertThat(u.getRuolo()).isEqualTo(Ruolo.STAFF);
        assertThat(service.personale()).extracting(Utente::getId).contains(u.getId());
    }

    @Test
    void catalogoInizialeEVociUniche() {
        assertThat(catalogo.attivi()).hasSizeGreaterThan(10);
        assertThatThrownBy(() -> catalogo.salva(new TipoIntervento("regolazione CAMBIO", 20)))
                .isInstanceOf(ErroreDominio.class);
        assertThatThrownBy(() -> catalogo.salva(new TipoIntervento("Troppo breve", 2)))
                .isInstanceOf(ConstraintViolationException.class);
        TipoIntervento t = catalogo.salva(new TipoIntervento("Montaggio parafanghi", 25));
        t.setAttivo(false);
        catalogo.salva(t);
        assertThat(catalogo.attivi()).extracting(TipoIntervento::getNome).doesNotContain("Montaggio parafanghi");
        assertThat(catalogo.tutti()).extracting(TipoIntervento::getNome).contains("Montaggio parafanghi");
    }
}
