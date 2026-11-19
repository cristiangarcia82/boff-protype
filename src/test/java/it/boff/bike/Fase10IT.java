package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.funzionalita.Funzionalita;
import it.boff.bike.funzionalita.FunzionalitaService;
import it.boff.bike.lavoro.Approvazione;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.notifiche.RecuperoPasswordService;
import it.boff.bike.privacy.PrivacyService;
import it.boff.bike.utente.DatiAnagrafica;
import it.boff.bike.utente.DatiRegistrazione;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fase 10: clienti che non usano l'app (arrivano in negozio, si gestiscono al telefono). */
@IntegrationTest
class Fase10IT {

    @Autowired UtenteService utenti;
    @Autowired BiciclettaService biciclette;
    @Autowired LavoroService lavori;
    @Autowired CatalogoService catalogo;
    @Autowired RecuperoPasswordService recupero;
    @Autowired FunzionalitaService funzionalita;
    @Autowired PrivacyService privacy;

    @AfterEach
    void ripristina() {
        funzionalita.imposta(Funzionalita.APPROVAZIONE_EXTRA, false);
    }

    static String telefono() {
        return "+39 3" + ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999);
    }

    static DatiAnagrafica senzaEmail(String telefono) {
        DatiAnagrafica d = new DatiAnagrafica();
        d.setNome("Mario");
        d.setCognome("Banco");
        d.setTelefono(telefono);
        d.setEmail("  ");
        return d;
    }

    @Test
    void clienteSenzaEmailDalBancoAlRitiro() {
        String tel = telefono();
        Utente c = utenti.creaCliente(senzaEmail(tel), true);
        assertThat(c.getEmail()).isNull();
        assertThat(c.usaApp()).isFalse();
        // due clienti senza email non vanno in conflitto
        utenti.creaCliente(senzaEmail(telefono()), true);

        assertThat(utenti.cercaClienti(tel.substring(4))).extracting(Utente::getId).contains(c.getId());

        Long bici = biciclette.aggiungi(c.getId(), DatiBicicletta.di("Bianchi", "Spillo", Tipologia.CITTA)).getId();
        Long tipo = catalogo.attivi().get(0).getId();
        Long lavoro = lavori.creaAlBanco(bici, List.of(tipo), "Arrivato senza app").getId();
        lavori.dettaglio(lavoro).getRighe().forEach(r -> lavori.segnaRiga(r.getId(), true));
        lavori.segnaPronto(lavoro); // nessuna email né push: non deve fallire
        lavori.ritira(lavoro);
        assertThat(lavori.dettaglio(lavoro).getStato()).isEqualTo(StatoLavoro.RITIRATO);
    }

    @Test
    void invitoSoloDopoAverAggiuntoLEmail() {
        Utente c = utenti.creaCliente(senzaEmail(telefono()), true);
        assertThatThrownBy(() -> recupero.invitaCliente(c.getId()))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non ha un'email");

        String occupata = UtenteServiceIT.email();
        utenti.registraCliente(UtenteServiceIT.registrazione(occupata));
        DatiAnagrafica conDoppione = DatiAnagrafica.da(c);
        conDoppione.setEmail(occupata);
        assertThatThrownBy(() -> utenti.aggiornaAnagrafica(c.getId(), conDoppione))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("Esiste già");

        String nuova = UtenteServiceIT.email();
        DatiAnagrafica conEmail = DatiAnagrafica.da(c);
        conEmail.setEmail(nuova);
        Utente aggiornato = utenti.aggiornaAnagrafica(c.getId(), conEmail);
        assertThat(aggiornato.usaApp()).isTrue();
        recupero.invitaCliente(c.getId());

        // una volta impostata, l'email non si cambia più dal negozio
        DatiAnagrafica altra = DatiAnagrafica.da(aggiornato);
        altra.setEmail(UtenteServiceIT.email());
        assertThat(utenti.aggiornaAnagrafica(c.getId(), altra).getEmail()).isEqualTo(nuova.toLowerCase());
    }

    @Test
    void registrazioneEPersonaleRichiedonoLEmail() {
        DatiRegistrazione cliente = UtenteServiceIT.registrazione(null);
        assertThatThrownBy(() -> utenti.registraCliente(cliente)).isInstanceOf(ConstraintViolationException.class);
        DatiRegistrazione staff = UtenteServiceIT.registrazione("");
        assertThatThrownBy(() -> utenti.creaCollaboratore(staff)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void ilNegozioRegistraLaRispostaDataAlTelefono() {
        funzionalita.imposta(Funzionalita.APPROVAZIONE_EXTRA, true);
        Utente c = utenti.creaCliente(senzaEmail(telefono()), true);
        Long bici = biciclette.aggiungi(c.getId(), DatiBicicletta.di("Trek", "FX", Tipologia.CITTA)).getId();
        Long lavoro = lavori.creaAlBanco(bici, List.of(catalogo.attivi().get(0).getId()), null).getId();
        Long extra = lavori.proponiExtra(lavoro, null, "Copertone posteriore", 15, "Tagliato").getId();

        lavori.registraRispostaExtra(extra, true);
        assertThat(lavori.dettaglio(lavoro).getRighe()).filteredOn(r -> r.getId().equals(extra)).singleElement()
                .satisfies(r -> assertThat(r.getApprovazione()).isEqualTo(Approvazione.APPROVATA));
        assertThatThrownBy(() -> lavori.registraRispostaExtra(extra, false))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non è in attesa");
    }

    @Test
    void informativaConfermataAlBanco() {
        assertThatThrownBy(() -> utenti.creaCliente(senzaEmail(telefono()), false))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("informativa");
        assertThat(utenti.creaCliente(senzaEmail(telefono()), true).getPrivacyAccettataIl()).isNotNull();
    }

    @Test
    void ilNegozioCancellaIDatiSuRichiesta() {
        String tel = telefono();
        Utente c = utenti.creaCliente(senzaEmail(tel), true);
        Long bici = biciclette.aggiungi(c.getId(), DatiBicicletta.di("Olmo", "Gran Turismo", Tipologia.STRADA)).getId();
        Long lavoro = lavori.creaAlBanco(bici, List.of(catalogo.attivi().get(0).getId()), "Note personali").getId();

        assertThat(privacy.esporta(c.getId())).contains("Olmo", "Note personali", tel);
        assertThatThrownBy(() -> privacy.eliminaSuRichiesta(c.getId()))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("lavoro ancora aperto");

        lavori.annulla(lavoro);
        privacy.eliminaSuRichiesta(c.getId());
        Utente anonimo = utenti.trova(c.getId());
        assertThat(anonimo.isEliminato()).isTrue();
        assertThat(anonimo.getTelefono()).isNull();
        assertThat(anonimo.getEmail()).isNull();
        assertThat(utenti.cercaClienti(tel.substring(4))).as("non compare più nelle ricerche").isEmpty();
        assertThat(lavori.lavoriDelCliente(c.getId())).singleElement()
                .satisfies(l -> assertThat(l.getNoteCliente()).isNull());
        assertThatThrownBy(() -> privacy.eliminaSuRichiesta(c.getId()))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("già");
    }
}
