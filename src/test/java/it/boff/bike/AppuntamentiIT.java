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

    @Autowired it.boff.bike.lavoro.LavoroService lavori;
    @Autowired it.boff.bike.pianificazione.PianificazioneService pianificazione;
    @Autowired it.boff.bike.catalogo.CatalogoService catalogo;

    /** Un giorno (mar–ven nei dati demo) in cui Cucciolo e Mario sono liberi negli orari usati dai test. */
    LocalDate giornoLibero(int scarto) {
        LocalDate g = calendario.oggi().plusDays(2 + scarto);
        for (int i = 0; i < 60; i++, g = g.plusDays(1)) {
            List<LocalTime> diLuca = appuntamenti.orariLiberiDi(luca, g, 45, null);
            List<LocalTime> diMario = appuntamenti.orariLiberiDi(mario, g, 45, null);
            if (diLuca.containsAll(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(11, 0)))
                    && diMario.containsAll(List.of(LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(15, 0)))) return g;
        }
        throw new IllegalStateException("nessun giorno libero nei dati demo");
    }

    @Test
    void ilClienteSceglieUnOrarioLiberoEIlNegozioConferma() {
        LocalDate g = giornoLibero(0);
        Map<LocalDate, List<LocalTime>> liberi = appuntamenti.orariLiberi(consulenza.getId(), g, g);
        assertThat(liberi.get(g)).contains(LocalTime.of(9, 0), LocalTime.of(15, 0))
                .as("45 minuti: alle 12 non si finisce prima della pausa delle 12:30").doesNotContain(LocalTime.of(12, 0));

        Appuntamento a = appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(9, 0), null, "Cerco una gravel");
        assertThat(a.getStato()).isEqualTo(StatoAppuntamento.RICHIESTO);
        assertThat(a.getOraFine()).isEqualTo(LocalTime.of(9, 45));
        Long chi = a.getUtente().getId();
        assertThat(appuntamenti.orariLiberiDi(chi, g, 45, null)).as("lo spazio è tenuto")
                .doesNotContain(LocalTime.of(9, 0), LocalTime.of(9, 30)).contains(LocalTime.of(10, 0));
        // alle 9:30 si possono prenotare tante consulenze quante sono le persone ancora libere, poi nessuna
        long liberiAlle930 = calendario.disponibilita(g, g).personale().stream()
                .filter(p -> appuntamenti.orariLiberiDi(p.getId(), g, 45, null).contains(LocalTime.of(9, 30))).count();
        java.util.Set<Long> persone = new java.util.HashSet<>(List.of(chi));
        for (int i = 0; i < liberiAlle930; i++) {
            Long altro = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
            assertThat(persone.add(appuntamenti.richiedi(altro, consulenza.getId(), g, LocalTime.of(9, 30), null, null)
                    .getUtente().getId())).as("ogni richiesta va a una persona diversa").isTrue();
        }
        Long ultimo = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        assertThatThrownBy(() -> appuntamenti.richiedi(ultimo, consulenza.getId(), g, LocalTime.of(9, 30), null, null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non è più libero");

        assertThat(appuntamenti.daConfermare()).extracting(Appuntamento::getId).contains(a.getId());
        appuntamenti.conferma(a.getId());
        assertThat(appuntamenti.dettaglio(a.getId()).getStato()).isEqualTo(StatoAppuntamento.CONFERMATO);
        assertThat(appuntamenti.daConfermare()).extracting(Appuntamento::getId).doesNotContain(a.getId());
    }

    @Test
    void sempreNellOrarioDiLavoro() {
        // lunedì mattina nessuno lavora (orario demo: Cucciolo solo il pomeriggio, Mario mai)
        LocalDate lunedi = calendario.oggi().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).plusWeeks(1);
        assertThatThrownBy(() -> appuntamenti.crea(cliente, consulenza.getId(), luca, lunedi, LocalTime.of(10, 0), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non è libero");
        assertThatThrownBy(() -> appuntamenti.crea(cliente, consulenza.getId(), null, lunedi, LocalTime.of(10, 0), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("Nessuno");

        LocalDate g = giornoLibero(3);
        Appuntamento a = appuntamenti.crea(cliente, consulenza.getId(), null, g, LocalTime.of(15, 0), "Porta la bici vecchia");
        assertThat(a.getStato()).isEqualTo(StatoAppuntamento.CONFERMATO);
        // spostamento: nell'orario sì, nella pausa pranzo no
        assertThatThrownBy(() -> appuntamenti.sposta(a.getId(), luca, g, LocalTime.of(13, 0)))
                .isInstanceOf(ErroreDominio.class);
        appuntamenti.sposta(a.getId(), luca, g, LocalTime.of(11, 0));
        Appuntamento spostato = appuntamenti.dettaglio(a.getId());
        assertThat(spostato.getUtente().getId()).isEqualTo(luca);
        assertThat(spostato.getOraInizio()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void unTurnoDiVenditaToglieTempoAdAppuntamentiEOfficina() {
        LocalDate g = giornoLibero(5);
        long primaOfficina = calendario.disponibilita(g, g).minuti(luca, g, it.boff.bike.calendario.TipoFascia.MANUTENZIONE);
        var turno = calendario.aggiungiTurnoVendita(luca, g, LocalTime.of(9, 0), LocalTime.of(11, 0), "Promozione");
        assertThat(appuntamenti.orariLiberiDi(luca, g, 45, null))
                .doesNotContain(LocalTime.of(9, 0), LocalTime.of(10, 0)).contains(LocalTime.of(11, 0));
        assertThat(calendario.disponibilita(g, g).minuti(luca, g, it.boff.bike.calendario.TipoFascia.MANUTENZIONE))
                .isEqualTo(primaOfficina - 120);
        // fuori orario o sovrapposto: rifiutato
        assertThatThrownBy(() -> calendario.aggiungiTurnoVendita(luca, g, LocalTime.of(13, 0), LocalTime.of(14, 0), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non lavora");
        assertThatThrownBy(() -> calendario.aggiungiTurnoVendita(luca, g, LocalTime.of(10, 0), LocalTime.of(11, 30), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("sovrappone");
        calendario.rimuoviTurnoVendita(turno.getId());
        assertThat(appuntamenti.orariLiberiDi(luca, g, 45, null)).contains(LocalTime.of(9, 0));
    }

    @Test
    void unAppuntamentoHaLaPrecedenzaSuiLavoriDiOfficina() {
        Long b = biciclette.aggiungi(cliente, DatiBicicletta.di("Scott", "Addict", Tipologia.STRADA)).getId();
        var catena = catalogo.attivi().stream().filter(t -> t.getNome().equals("Sostituzione catena")).findFirst().orElseThrow();
        Long lavoro = lavori.creaAlBanco(b, List.of(catena.getId()), null).getId();
        var consegna = appuntamenti.tipiAttivi().stream().filter(t -> t.getNome().equals("Consegna bici nuova")).findFirst().orElseThrow();
        Appuntamento app = null;
        for (var pezzo : pianificazione.diLavoro(lavoro)) {
            try {
                app = appuntamenti.crea(cliente, consegna.getId(), pezzo.getUtente().getId(), pezzo.getData(),
                        pezzo.getOraInizio(), null);
                break;
            } catch (ErroreDominio e) {
                // pezzo a fine fascia: l'appuntamento non ci sta, si prova il successivo
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(app != null, "nessun pezzo adatto nei dati di prova");
        Appuntamento fissato = app;
        assertThat(pianificazione.diLavoro(lavoro)).as("il lavoro si sposta attorno all'appuntamento")
                .noneMatch(x -> x.getUtente().getId().equals(fissato.getUtente().getId())
                        && x.getData().equals(fissato.getData()) && x.intervallo().siSovrappone(fissato.intervallo()));
        assertThat(pianificazione.diLavoro(lavoro).stream().mapToLong(x -> x.intervallo().minuti()).sum())
                .isEqualTo((long) catena.getDurataStandardMin());
        appuntamenti.annulla(fissato.getId());
    }

    @Test
    void unaAssenzaToglieGliOrari() {
        LocalDate g = giornoLibero(6);
        calendario.aggiungiAssenza(luca, g, g, MotivoAssenza.FERIE, null);
        assertThat(appuntamenti.orariLiberiDi(luca, g, 45, null)).isEmpty();
    }

    @Test
    void ilClienteAnnullaSoloISuoiEHaUnLimite() {
        LocalDate g = giornoLibero(9);
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

        Long chi = a.getUtente().getId();
        appuntamenti.annullaDelCliente(cliente, a.getId());
        assertThat(appuntamenti.dettaglio(a.getId()).getStato()).isEqualTo(StatoAppuntamento.ANNULLATO);
        assertThat(appuntamenti.orariLiberiDi(chi, g, 45, null)).as("lo spazio torna libero").contains(LocalTime.of(9, 0));
    }

    @Test
    void laCancellazioneDeiDatiAnnullaGliAppuntamenti() {
        LocalDate g = giornoLibero(12);
        Appuntamento a = appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(9, 0), null, "Note personali");
        assertThat(privacy.esporta(cliente)).contains("Consulenza per l'acquisto", "Note personali");
        privacy.eliminaAccount(cliente);
        Appuntamento dopo = appuntamenti.dettaglio(a.getId());
        assertThat(dopo.getStato()).isEqualTo(StatoAppuntamento.ANNULLATO);
        assertThat(dopo.getNoteCliente()).isNull();
    }

    @Test
    void dueRichiesteContemporaneeNonPrenotanoLoStessoOrario() throws Exception {
        LocalDate g = giornoLibero(15);
        int tentativi = 6;
        var pronti = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(tentativi);
        List<java.util.concurrent.Future<Boolean>> esiti = new java.util.ArrayList<>();
        for (int i = 0; i < tentativi; i++) {
            esiti.add(pool.submit(() -> {
                pronti.await();
                try {
                    appuntamenti.crea(cliente, consulenza.getId(), luca, g, LocalTime.of(10, 0), null);
                    return true;
                } catch (ErroreDominio e) {
                    return false;
                }
            }));
        }
        pronti.countDown();
        long riusciti = 0;
        for (var f : esiti) if (f.get(30, java.util.concurrent.TimeUnit.SECONDS)) riusciti++;
        pool.shutdown();
        assertThat(riusciti).as("un solo appuntamento con Cucciolo alle 10").isEqualTo(1);
        assertThat(appuntamenti.tra(g, g)).filteredOn(a -> a.getUtente().getId().equals(luca)
                && a.getOraInizio().equals(LocalTime.of(10, 0))).hasSize(1);
    }

    @Test
    void unTurnoNonCopreUnAppuntamentoEUnAppuntamentoNonVaNelPassato() {
        LocalDate g = giornoLibero(18);
        appuntamenti.crea(cliente, consulenza.getId(), luca, g, LocalTime.of(10, 0), null);
        assertThatThrownBy(() -> calendario.aggiungiTurnoVendita(luca, g, LocalTime.of(9, 0), LocalTime.of(12, 0), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("appuntamento");
        assertThatThrownBy(() -> appuntamenti.crea(cliente, consulenza.getId(), luca, calendario.oggi().minusDays(1), LocalTime.of(10, 0), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("già passato");
    }

    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void richiestaScadutaNonSiConfermaESvoltoSoloDopoLInizio() {
        LocalDate g = giornoLibero(7);
        Appuntamento futuro = appuntamenti.crea(cliente, consulenza.getId(), mario, g, LocalTime.of(9, 0), null);
        assertThatThrownBy(() -> appuntamenti.segnaSvolto(futuro.getId()))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("non è ancora iniziato");

        Appuntamento richiesta = appuntamenti.richiedi(cliente, consulenza.getId(), g, LocalTime.of(9, 30), null, null);
        // la richiesta resta senza risposta e il giorno passa
        LocalDate ieri = calendario.oggi().minusDays(1);
        jdbc.update("UPDATE appuntamento SET data = ? WHERE id IN (?, ?)", ieri, richiesta.getId(), futuro.getId());
        assertThatThrownBy(() -> appuntamenti.conferma(richiesta.getId()))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("già passato");
        assertThat(appuntamenti.daConfermare()).as("resta in elenco per rifiutarla o spostarla")
                .extracting(Appuntamento::getId).contains(richiesta.getId());
        assertThat(appuntamenti.daChiudere()).extracting(Appuntamento::getId)
                .contains(futuro.getId()).doesNotContain(richiesta.getId());
        appuntamenti.segnaSvolto(futuro.getId());
        assertThat(appuntamenti.dettaglio(futuro.getId()).getStato()).isEqualTo(StatoAppuntamento.SVOLTO);
        assertThat(appuntamenti.daChiudere()).extracting(Appuntamento::getId).doesNotContain(futuro.getId());
        appuntamenti.annulla(richiesta.getId());
    }

    @Test
    void spaziLiberiPerScegliereQuandoFareUnLavoro() {
        LocalDate g = giornoLibero(9);
        var prima = pianificazione.spaziLiberi(List.of(), luca, g, 1, 60).get(0);
        appuntamenti.crea(cliente, consulenza.getId(), luca, g, LocalTime.of(9, 0), null);
        var dopo = pianificazione.spaziLiberi(List.of(), luca, g, 1, 60).get(0);
        assertThat(dopo.giorno()).isEqualTo(g);
        assertThat(dopo.minutiLiberi()).as("l'appuntamento toglie 45 minuti").isEqualTo(prima.minutiLiberi() - 45);
        assertThat(dopo.inizi()).doesNotContain(LocalTime.of(9, 0), LocalTime.of(9, 30))
                .allSatisfy(t -> assertThat(t.getMinute() % 15).isZero());
        if (!dopo.inizi().isEmpty()) {
            // un orario proposto si può davvero usare
            Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Bianchi", "Via Nirone", Tipologia.STRADA)).getId();
            Long lavoro = lavori.creaAlBanco(bici, List.of(catalogo.attivi().get(0).getId()), null).getId();
            var pezzi = pianificazione.diLavoro(lavoro).stream().map(it.boff.bike.pianificazione.Assegnazione::getId).toList();
            long durata = pianificazione.diLavoro(lavoro).stream().mapToLong(a -> a.intervallo().minuti()).sum();
            var spazi = pianificazione.spaziLiberi(pezzi, luca, g, 1, durata).get(0);
            if (!spazi.inizi().isEmpty()) {
                LocalTime ora = spazi.inizi().get(0);
                pianificazione.spostaTutti(pezzi, luca, g, ora);
                assertThat(pianificazione.diLavoro(lavoro).get(0).getOraInizio()).isEqualTo(ora);
                assertThat(pianificazione.diLavoro(lavoro)).allMatch(it.boff.bike.pianificazione.Assegnazione::isBloccata);
            }
            lavori.annulla(lavoro);
        }
    }
}
