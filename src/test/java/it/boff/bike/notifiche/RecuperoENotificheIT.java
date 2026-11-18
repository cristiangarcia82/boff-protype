package it.boff.bike.notifiche;

import it.boff.bike.IntegrationTest;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.LavoroCambiato;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.utente.DatiRegistrazione;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import it.boff.bike.utente.UtenteService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class RecuperoENotificheIT {

    @Autowired RecuperoPasswordService recupero;
    @Autowired UtenteService utenti;
    @Autowired UtenteRepository repo;
    @Autowired PasswordEncoder encoder;
    @Autowired NotificheLavori notificheLavori;
    @Autowired LavoroService lavori;
    @Autowired BiciclettaService biciclette;
    @Autowired CatalogoService catalogo;

    Utente nuovoCliente() {
        DatiRegistrazione d = new DatiRegistrazione();
        d.setNome("Rita");
        d.setCognome("Gialli");
        d.setEmail(UUID.randomUUID() + "@test.it");
        d.setTelefono("333 1112223");
        d.setPassword("vecchiapass");
        d.setPrivacyAccettata(true);
        return utenti.registraCliente(d);
    }

    @Test
    void linkMonousoPerImpostareLaPassword() {
        Utente u = nuovoCliente();
        String token = recupero.inviaLink(u, false);
        assertThat(recupero.tokenValido(token)).isTrue();
        assertThat(recupero.tokenValido("inventato")).isFalse();

        assertThatThrownBy(() -> recupero.reimposta(token, "corta")).isInstanceOf(ConstraintViolationException.class);
        recupero.reimposta(token, "nuovapassword");
        assertThat(encoder.matches("nuovapassword", repo.findById(u.getId()).orElseThrow().getPasswordHash())).isTrue();

        // monouso
        assertThat(recupero.tokenValido(token)).isFalse();
        assertThatThrownBy(() -> recupero.reimposta(token, "altrapassword")).isInstanceOf(ErroreDominio.class);
    }

    @Test
    void emailSconosciutaNessunErroreETroppeRichiesteLimitate() {
        recupero.richiedi("nessuno@nessuno.it"); // nessuna eccezione: non si rivela se l'email esiste
        Utente u = nuovoCliente();
        assertThat(recupero.inviaLink(u, false)).isNotNull();
        assertThat(recupero.inviaLink(u, false)).isNotNull();
        assertThat(recupero.inviaLink(u, true)).isNotNull();
        assertThat(recupero.inviaLink(u, false)).as("massimo 3 link all'ora").isNull();
    }

    @Test
    void messaggiPerICambiDiStato() {
        Utente c = nuovoCliente();
        Long bici = biciclette.aggiungi(c.getId(), DatiBicicletta.di("Bottecchia", "8Avenue", Tipologia.STRADA)).getId();
        Long tipo = catalogo.attivi().get(0).getId();
        Long id = lavori.annuncia(c.getId(), bici, List.of(tipo), null).getId();

        var annuncio = notificheLavori.messaggio(new LavoroCambiato(id, null, StatoLavoro.ANNUNCIATO));
        assertThat(annuncio.alPersonale()).isTrue();
        assertThat(annuncio.testo()).contains("Bottecchia 8Avenue", "Rita Gialli");

        lavori.checkIn(id);
        var arrivata = notificheLavori.messaggio(new LavoroCambiato(id, StatoLavoro.ANNUNCIATO, StatoLavoro.IN_NEGOZIO));
        assertThat(arrivata.destinatario()).isEqualTo(c.getId());
        assertThat(arrivata.titolo()).isEqualTo("Bici ricevuta");

        var data = notificheLavori.messaggio(new LavoroCambiato(id, StatoLavoro.IN_NEGOZIO, StatoLavoro.PIANIFICATO));
        assertThat(data.testo()).contains("sarà pronta indicativamente");

        // un ricalcolo che non cambia stato non genera notifiche
        assertThat(notificheLavori.messaggio(new LavoroCambiato(id, StatoLavoro.PIANIFICATO, StatoLavoro.PIANIFICATO))).isNull();

        lavori.segnaPronto(id);
        var pronta = notificheLavori.messaggio(new LavoroCambiato(id, StatoLavoro.PIANIFICATO, StatoLavoro.PRONTO));
        assertThat(pronta.titolo()).isEqualTo("La tua bici è pronta!");
        assertThat(pronta.emailDiRiserva()).isTrue();
    }
}
