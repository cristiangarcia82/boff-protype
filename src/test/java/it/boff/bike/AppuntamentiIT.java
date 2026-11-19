package it.boff.bike;

import it.boff.bike.appuntamento.Appuntamento;
import it.boff.bike.appuntamento.AppuntamentoService;
import it.boff.bike.appuntamento.StatoAppuntamento;
import it.boff.bike.appuntamento.TipoAppuntamento;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.MotivoAssenza;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.privacy.PrivacyService;
import it.boff.bike.utente.UtenteRepository;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fase 12: appuntamenti di consulenza nelle ore di vendita. */
@IntegrationTest
class AppuntamentiIT {

    @Autowired AppuntamentoService appuntamenti;
    @Autowired UtenteService utenti;
    @Autowired UtenteRepository utenteRepo;
    @Autowired BiciclettaService biciclette;
    @Autowired CalendarioService calendario;
    @Autowired PrivacyService privacy;

    Long cliente;
    TipoAppuntamento consulenza;
    Long mario, luca;

    @BeforeEach
    void prepara() {
        cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        consulenza = appuntamenti.tipiAttivi().stream().filter(t -> t.getNome().equals("Consulenza per l'acquisto"))
                .findFirst().orElseThrow();
        mario = utenteRepo.findByEmail("titolare@demo.it").orElseThrow().getId();
        luca = utenteRepo.findByEmail("collaboratore@demo.it").orElseThrow().getId();
    }

    /** Un giorno nelle prossime settimane in cui Luca è in vendita alle 9 (orario demo: mar–ven 9–12:30). */
    LocalDate giornoConVenditaLuca(int scarto) {
        LocalDate g = calendario.oggi().plusDays(2 + scarto);
        for (int i = 0; i < 60; i++, g = g.plusDays(1)) {
            // gli orari usati dai test devono essere davvero liberi (altri test o i dati demo li occupano)
            List<LocalTime> diLuca = appuntamenti.orariLiberiDi(luca, g, 45, null);
            if (diLuca.containsAll(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(11, 0)))
                    && appuntamenti.orariLiberiDi(mario, g, 45, null).contains(LocalTime.of(15, 0))) return g;
        }
        throw new IllegalStateException("nessun giorno di vendita per Luca nei dati demo");
    }

    @Test
    void ilClienteSceglieUnOrarioLiberoEIlNegozioConferma() {
        LocalDate g = giornoConVenditaLuca(0);
        Map<LocalDate, List<LocalTime>> liberi = appuntamenti.orariLiberi(consulenza.getId(), g, g);
        assertThat(liberi.get(g)).contains(LocalTime.of(9, 0), LocalTime.of(9, 30))
                .as("45 minuti: le 12 non bastano prima delle 12:30").doesNotContain(LocalTime.of(12, 0));

        Appuntamento a = appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(9, 0), null, "Cerco una gravel");
        assertThat(a.getStato()).isEqualTo(StatoAppuntamento.RICHIESTO);
        assertThat(a.getUtente().getId()).as("alle 9 è in vendita Luca").isEqualTo(luca);
        assertThat(a.getOraFine()).isEqualTo(LocalTime.of(9, 45));
        // lo spazio è tenuto: nessun altro alle 9 né alle 9:30
        assertThat(appuntamenti.orariLiberiDi(luca, g, 45, null)).doesNotContain(LocalTime.of(9, 0), LocalTime.of(9, 30))
                .contains(LocalTime.of(10, 0));
        Long altro = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        assertThatThrownBy(() -> appuntamenti.richiedi(altro, consulenza.getId(), g, LocalTime.of(9, 30), null, null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non è più libero");

        assertThat(appuntamenti.daConfermare()).extracting(Appuntamento::getId).contains(a.getId());
        appuntamenti.conferma(a.getId());
        assertThat(appuntamenti.dettaglio(a.getId()).getStato()).isEqualTo(StatoAppuntamento.CONFERMATO);
        assertThat(appuntamenti.daConfermare()).extracting(Appuntamento::getId).doesNotContain(a.getId());
    }

    @Test
    void sempreNelleOreDiVendita() {
        LocalDate g = giornoConVenditaLuca(3);
        // alle 15 Luca è in officina (orario demo): non si può fissare con lui
        assertThatThrownBy(() -> appuntamenti.crea(cliente, consulenza.getId(), luca, g, LocalTime.of(15, 0), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("ore di vendita");
        // dal negozio, senza scegliere la persona: chi è in vendita a quell'ora (Mario il pomeriggio)
        Appuntamento a = appuntamenti.crea(cliente, consulenza.getId(), null, g, LocalTime.of(15, 0), "Porta la bici vecchia");
        assertThat(a.getStato()).isEqualTo(StatoAppuntamento.CONFERMATO);
        assertThat(a.getUtente().getId()).isEqualTo(mario);

        // spostamento: dentro la vendita sì, fuori no
        assertThatThrownBy(() -> appuntamenti.sposta(a.getId(), mario, g, LocalTime.of(10, 0)))
                .isInstanceOf(ErroreDominio.class);
        appuntamenti.sposta(a.getId(), luca, g, LocalTime.of(11, 0));
        Appuntamento spostato = appuntamenti.dettaglio(a.getId());
        assertThat(spostato.getUtente().getId()).isEqualTo(luca);
        assertThat(spostato.getOraInizio()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void unaAssenzaToglieGliOrari() {
        LocalDate g = giornoConVenditaLuca(6);
        calendario.aggiungiAssenza(luca, g, g, MotivoAssenza.FERIE, null);
        assertThat(appuntamenti.orariLiberiDi(luca, g, 45, null)).isEmpty();
        assertThat(appuntamenti.orariLiberi(consulenza.getId(), g, g).getOrDefault(g, List.of()))
                .doesNotContain(LocalTime.of(9, 0));
    }

    @Test
    void ilClienteAnnullaSoloISuoiEHaUnLimite() {
        LocalDate g = giornoConVenditaLuca(9);
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Specialized", "Diverge", Tipologia.GRAVEL)).getId();
        Appuntamento a = appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(9, 0), bici, null);
        Long altro = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        assertThatThrownBy(() -> appuntamenti.annullaDelCliente(altro, a.getId())).isInstanceOf(ErroreDominio.class);
        // la bici deve essere sua
        assertThatThrownBy(() -> appuntamenti.richiedi(altro, consulenza.getId(), g, LocalTime.of(10, 0), bici, null))
                .isInstanceOf(ErroreDominio.class);

        appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(10, 0), null, null);
        appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(11, 0), null, null);
        assertThatThrownBy(() -> appuntamenti.richiedi(cliente, consulenza.getId(), g.plusDays(7), LocalTime.of(9, 0), null, null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("già 3");

        appuntamenti.annullaDelCliente(cliente, a.getId());
        assertThat(appuntamenti.dettaglio(a.getId()).getStato()).isEqualTo(StatoAppuntamento.ANNULLATO);
        assertThat(appuntamenti.orariLiberiDi(luca, g, 45, null)).as("lo spazio torna libero").contains(LocalTime.of(9, 0));
    }

    @Test
    void laCancellazioneDeiDatiAnnullaGliAppuntamenti() {
        LocalDate g = giornoConVenditaLuca(12);
        Appuntamento a = appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(9, 0), null, "Note personali");
        assertThat(privacy.esporta(cliente)).contains("Consulenza per l'acquisto", "Note personali");
        privacy.eliminaAccount(cliente);
        Appuntamento dopo = appuntamenti.dettaglio(a.getId());
        assertThat(dopo.getStato()).isEqualTo(StatoAppuntamento.ANNULLATO);
        assertThat(dopo.getNoteCliente()).isNull();
    }
}
