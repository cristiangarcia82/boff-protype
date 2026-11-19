package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaRepository;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.funzionalita.Funzionalita;
import it.boff.bike.funzionalita.FunzionalitaService;
import it.boff.bike.lavoro.Approvazione;
import it.boff.bike.lavoro.InterventoExtraProposto;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.promemoria.PromemoriaService;
import it.boff.bike.statistiche.StatisticheService;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@RecordApplicationEvents
class FunzionalitaIT {

    @Autowired FunzionalitaService funzionalita;
    @Autowired LavoroService lavori;
    @Autowired PianificazioneService pianificazione;
    @Autowired UtenteService utenti;
    @Autowired BiciclettaService biciclette;
    @Autowired BiciclettaRepository biciRepo;
    @Autowired CatalogoService catalogo;
    @Autowired StatisticheService statistiche;
    @Autowired PromemoriaService promemoria;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEvents eventi;

    Long cliente;
    Long bici;
    TipoIntervento catena;

    @BeforeEach
    void prepara() {
        cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Pinarello", "F5", Tipologia.STRADA)).getId();
        catena = catalogo.attivi().stream().filter(t -> t.getNome().equals("Sostituzione catena")).findFirst().orElseThrow();
    }

    @AfterEach
    void spegniTutto() {
        for (Funzionalita f : Funzionalita.values()) funzionalita.imposta(f, false);
    }

    private int minutiInCalendario(Long lavoroId) {
        return pianificazione.diLavoro(lavoroId).stream().mapToInt(a -> (int) a.intervallo().minuti()).sum();
    }

    private RigaLavoro riga(Long lavoroId, Long rigaId) {
        return lavori.dettaglio(lavoroId).getRighe().stream().filter(r -> r.getId().equals(rigaId)).findFirst().orElseThrow();
    }

    @Test
    void tutteSpenteDiDefault() {
        for (Funzionalita f : Funzionalita.values()) assertThat(funzionalita.attiva(f)).as(f.name()).isFalse();
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        assertThatThrownBy(() -> lavori.proponiExtra(id, catena.getId(), null, null, null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non è attiva");
        Long r = lavori.dettaglio(id).getRighe().get(0).getId();
        lavori.segnaRiga(r, true);
        assertThatThrownBy(() -> lavori.registraTempoEffettivo(r, 30)).isInstanceOf(ErroreDominio.class);
    }

    @Test
    void approvazioneDiUnInterventoExtra() {
        funzionalita.imposta(Funzionalita.APPROVAZIONE_EXTRA, true);
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        Long extra = lavori.proponiExtra(id, null, "Sostituzione pastiglie posteriori", 25, "Consumate al 90%").getId();

        assertThat(riga(id, extra).getApprovazione()).isEqualTo(Approvazione.DA_APPROVARE);
        assertThat(eventi.stream(InterventoExtraProposto.class).filter(e -> e.rigaId().equals(extra))).hasSize(1);
        // in attesa: fuori dal calendario e la bici non può essere segnata pronta
        assertThat(minutiInCalendario(id)).isEqualTo(catena.getDurataStandardMin());
        assertThatThrownBy(() -> lavori.segnaPronto(id)).isInstanceOf(ErroreDominio.class).hasMessageContaining("risposta");

        // un altro cliente non può rispondere
        Long altro = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        assertThatThrownBy(() -> lavori.rispondiExtra(altro, extra, true)).isInstanceOf(ErroreDominio.class);

        lavori.rispondiExtra(cliente, extra, true);
        assertThat(riga(id, extra).getApprovazione()).isEqualTo(Approvazione.APPROVATA);
        assertThat(minutiInCalendario(id)).isEqualTo(catena.getDurataStandardMin() + 25);
        assertThatThrownBy(() -> lavori.rispondiExtra(cliente, extra, false))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("già risposto");
    }

    @Test
    void unInterventoRifiutatoNonSiFa() {
        funzionalita.imposta(Funzionalita.APPROVAZIONE_EXTRA, true);
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        Long extra = lavori.proponiExtra(id, catena.getId(), null, null, null).getId();
        lavori.rispondiExtra(cliente, extra, false);
        assertThat(lavori.dettaglio(id).minutiDaFare()).isEqualTo(catena.getDurataStandardMin());
        lavori.segnaPronto(id);
        assertThat(lavori.dettaglio(id).getStato()).isEqualTo(StatoLavoro.PRONTO);
        assertThat(riga(id, extra).isCompletata()).as("il rifiutato non risulta fatto").isFalse();
    }

    @Test
    void tempoEffettivoENelleStatistiche() {
        funzionalita.imposta(Funzionalita.TEMPO_EFFETTIVO, true);
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        Long r = lavori.dettaglio(id).getRighe().get(0).getId();
        assertThatThrownBy(() -> lavori.registraTempoEffettivo(r, 30)).hasMessageContaining("Segna prima");
        lavori.segnaRiga(r, true);
        assertThatThrownBy(() -> lavori.registraTempoEffettivo(r, 0)).isInstanceOf(ErroreDominio.class);
        lavori.registraTempoEffettivo(r, 35);
        lavori.segnaPronto(id);
        assertThat(statistiche.tempiEffettivi(30)).anySatisfy(c -> {
            assertThat(c.intervento()).isEqualTo("Sostituzione catena");
            assertThat(c.effettivoMedio()).isGreaterThan(0);
        });
        // togliere la spunta cancella il tempo reale
        Long id2 = lavori.creaAlBanco(biciclette.aggiungi(cliente, DatiBicicletta.di("Bianchi", "Oltre", Tipologia.STRADA)).getId(),
                List.of(catena.getId()), null).getId();
        Long r2 = lavori.dettaglio(id2).getRighe().get(0).getId();
        lavori.segnaRiga(r2, true);
        lavori.registraTempoEffettivo(r2, 15);
        lavori.segnaRiga(r2, false);
        assertThat(riga(id2, r2).getMinutiEffettivi()).isNull();
    }

    @Test
    void promemoriaDopoIMesiIndicatiUnaVoltaSola() {
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        lavori.segnaPronto(id);
        lavori.ritira(id);
        jdbc.update("update lavoro set chiuso_il = now() - interval '3 months' where id = ?", id);
        // bici di un cliente che ha disattivato i promemoria
        Long schivo = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        Long biciSchivo = biciclette.aggiungi(schivo, DatiBicicletta.di("Atala", "City", Tipologia.CITTA)).getId();
        Long id2 = lavori.creaAlBanco(biciSchivo, List.of(catena.getId()), null).getId();
        lavori.segnaPronto(id2);
        lavori.ritira(id2);
        jdbc.update("update lavoro set chiuso_il = now() - interval '3 months' where id = ?", id2);
        utenti.impostaPromemoria(schivo, false);

        funzionalita.imposta(Funzionalita.PROMEMORIA_MANUTENZIONE, true);
        funzionalita.impostaMesiPromemoria(2);
        promemoria.invia();
        assertThat(biciRepo.findById(bici).orElseThrow().getPromemoriaInviatoIl()).isNotNull();
        assertThat(biciRepo.findById(biciSchivo).orElseThrow().getPromemoriaInviatoIl()).isNull();

        var primo = biciRepo.findById(bici).orElseThrow().getPromemoriaInviatoIl();
        promemoria.invia();
        assertThat(biciRepo.findById(bici).orElseThrow().getPromemoriaInviatoIl()).as("non si ripete").isEqualTo(primo);

        assertThatThrownBy(() -> funzionalita.impostaMesiPromemoria(0)).isInstanceOf(ErroreDominio.class);
        assertThatThrownBy(() -> funzionalita.impostaSogliaCarico(30)).isInstanceOf(ErroreDominio.class);
    }

    @Test
    void caricoDellOfficina() {
        var prima = pianificazione.carico(7);
        assertThat(prima.capacita()).as("i dati demo hanno ore di officina").isPositive();
        // un lavoro enorme riempie la settimana
        TipoIntervento lungo = catalogo.salva(new TipoIntervento("Restauro completo " + System.nanoTime(), 1440));
        Long id = lavori.creaAlBanco(bici, List.of(lungo.getId()), null).getId();
        // dimensionato sulla capacità reale: le ore libere cambiano con il giorno e l'ora in cui gira il test
        for (long m = 1440; m <= prima.capacita() + 1440; m += 1440) {
            lavori.aggiungiDaCatalogo(id, lungo.getId());
        }
        try {
            assertThat(pianificazione.carico(7).percento()).isGreaterThanOrEqualTo(90);
        } finally {
            lavori.annulla(id);
            lungo.setAttivo(false);
            catalogo.salva(lungo);
        }
    }
}
