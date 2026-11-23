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
import it.boff.bike.lavoro.LavoroService;
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

    @Test
    void unLavoroAnnunciatoSiFissaPrimaCheArriviLaBici() {
        LocalDate g = giornoLibero(11);
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Pinarello", "Dogma", Tipologia.STRADA)).getId();
        Long lavoro = lavori.annuncia(cliente, bici, List.of(catalogo.attivi().get(0).getId()), "Cambio che salta").getId();
        assertThat(pianificazione.diLavoro(lavoro)).as("senza bici non va in calendario da solo").isEmpty();

        pianificazione.fissaLavoro(lavoro, luca, g, LocalTime.of(9, 0));
        var pezzi = pianificazione.diLavoro(lavoro);
        assertThat(pezzi).isNotEmpty().allMatch(it.boff.bike.pianificazione.Assegnazione::isBloccata)
                .allMatch(a -> a.getData().equals(g) && a.getUtente().getId().equals(luca));
        var l = lavori.dettaglio(lavoro);
        assertThat(l.getStato()).isEqualTo(it.boff.bike.lavoro.StatoLavoro.ANNUNCIATO);
        assertThat(l.getArrivoPrevisto()).isEqualTo(g.atTime(9, 0));
        assertThat(l.getFinePrevista()).isEqualTo(g);
        assertThat(it.boff.bike.notifiche.NotificheLavori.messaggioArrivo(l).testo()).contains("entro le 9:00");

        // il ricalcolo completo non lo toglie, e gli appuntamenti non ci si sovrappongono
        pianificazione.ripianifica();
        assertThat(pianificazione.diLavoro(lavoro)).hasSameSizeAs(pezzi);
        assertThat(appuntamenti.orariLiberiDi(luca, g, 45, null)).doesNotContain(LocalTime.of(9, 0));

        // arriva la bici: il lavoro resta dov'era
        lavori.checkIn(lavoro);
        pianificazione.aggiorna();
        var dopo = lavori.dettaglio(lavoro);
        assertThat(dopo.getArrivoPrevisto()).isNull();
        assertThat(pianificazione.diLavoro(lavoro).get(0).getOraInizio()).isEqualTo(LocalTime.of(9, 0));
        assertThatThrownBy(() -> pianificazione.togliDalCalendario(lavoro)).isInstanceOf(ErroreDominio.class);
        lavori.annulla(lavoro);
    }

    @Test
    void unAnnunciatoFissatoSiPuoTogliereDalCalendario() {
        LocalDate g = giornoLibero(13);
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Colnago", "C64", Tipologia.STRADA)).getId();
        Long lavoro = lavori.annuncia(cliente, bici, List.of(catalogo.attivi().get(0).getId()), null).getId();
        pianificazione.fissaLavoro(lavoro, mario, g, null);
        assertThat(lavori.dettaglio(lavoro).getArrivoPrevisto()).isNotNull();
        pianificazione.togliDalCalendario(lavoro);
        assertThat(pianificazione.diLavoro(lavoro)).isEmpty();
        var l = lavori.dettaglio(lavoro);
        assertThat(l.getArrivoPrevisto()).isNull();
        assertThat(l.getFinePrevista()).isNull();
        lavori.annulla(lavoro);
    }

    @Test
    void ilRicalcoloNonCancellaIlPassato() {
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Bottecchia", "8avenue", Tipologia.STRADA)).getId();
        Long lavoro = lavori.creaAlBanco(bici, List.of(catalogo.attivi().get(0).getId()), null).getId();
        Long riga = lavori.dettaglio(lavoro).getRighe().get(0).getId();
        LocalDate ieri = calendario.oggi().minusDays(1);
        jdbc.update("INSERT INTO assegnazione (riga_lavoro_id, utente_id, data, ora_inizio, ora_fine, bloccata) VALUES (?, ?, ?, '09:00', '09:30', FALSE)",
                riga, mario, ieri);
        pianificazione.ripianifica();
        pianificazione.aggiorna();
        assertThat(pianificazione.diLavoro(lavoro)).as("ciò che era in calendario ieri resta, per rivederlo")
                .anyMatch(a -> a.getData().equals(ieri));
        lavori.annulla(lavoro);
    }

    @Test
    void unInterventoFattoOggiRestaInCalendario() {
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Olmo", "Bicicapace", Tipologia.CITTA)).getId();
        Long lavoro = lavori.creaAlBanco(bici, List.of(catalogo.attivi().get(0).getId()), null).getId();
        Long riga = lavori.dettaglio(lavoro).getRighe().get(0).getId();
        LocalDate oggi = calendario.oggi();
        // il calcolo lo aveva messo oggi (qualunque sia l'orario di oggi nei test)
        jdbc.update("DELETE FROM assegnazione WHERE riga_lavoro_id = ?", riga);
        jdbc.update("INSERT INTO assegnazione (riga_lavoro_id, utente_id, data, ora_inizio, ora_fine, bloccata) VALUES (?, ?, ?, '06:00', '06:30', FALSE)",
                riga, mario, oggi);
        lavori.segnaRiga(riga, true);
        pianificazione.aggiorna();
        pianificazione.ripianifica();
        assertThat(pianificazione.diLavoro(lavoro)).as("spuntato, resta visibile dove era")
                .anyMatch(a -> a.getData().equals(oggi) && a.getOraInizio().equals(LocalTime.of(6, 0)));
        lavori.annulla(lavoro);
    }

    @Test
    void lUltimaVoltaPerOgniInterventoDellaBici() {
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Bianchi", "Infinito", Tipologia.STRADA)).getId();
        var catena = catalogo.attivi().stream().filter(t -> t.getNome().equals("Sostituzione catena")).findFirst().orElseThrow();
        var freni = catalogo.attivi().stream().filter(t -> t.getNome().equals("Regolazione freni")).findFirst().orElseThrow();
        // un anno fa catena e freni, cinque mesi fa di nuovo la catena
        Long vecchio = lavori.creaAlBanco(bici, List.of(catena.getId(), freni.getId()), null).getId();
        lavori.dettaglio(vecchio).getRighe().forEach(r -> lavori.segnaRiga(r.getId(), true));
        lavori.segnaPronto(vecchio);
        lavori.ritira(vecchio);
        Long recente = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        lavori.dettaglio(recente).getRighe().forEach(r -> lavori.segnaRiga(r.getId(), true));
        lavori.segnaPronto(recente);
        lavori.ritira(recente);
        LocalDate oggi = calendario.oggi();
        jdbc.update("UPDATE lavoro SET pronto_il = now() - interval '365 days' WHERE id = ?", vecchio);
        jdbc.update("UPDATE lavoro SET pronto_il = now() - interval '150 days' WHERE id = ?", recente);
        Long annullato = lavori.creaAlBanco(bici, List.of(freni.getId()), null).getId();
        lavori.annulla(annullato);

        var ultimi = lavori.ultimiInterventi(bici);
        assertThat(ultimi).extracting(LavoroService.UltimoIntervento::intervento)
                .containsExactly("Sostituzione catena", "Regolazione freni");
        assertThat(ultimi.get(0).volte()).isEqualTo(2);
        assertThat(ultimi.get(0).quando()).isBetween(oggi.minusDays(152), oggi.minusDays(148));
        assertThat(ultimi.get(1).quando()).isBetween(oggi.minusDays(367), oggi.minusDays(363));
        assertThat(it.boff.bike.ui.componenti.Formati.tempoFa(ultimi.get(0).quando(), oggi)).isEqualTo("4 mesi fa");
        assertThat(lavori.ultimiInterventi(bici, recente)).first()
                .satisfies(u -> assertThat(u.intervento()).isEqualTo("Regolazione freni"));
    }

    @Test
    void ogniInterventoFattoRicordaQuandoEChi() {
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Ganna", "Strada", Tipologia.STRADA)).getId();
        var tipi = catalogo.attivi().stream().limit(2).map(t -> t.getId()).toList();
        Long lavoro = lavori.creaAlBanco(bici, tipi, null).getId();
        var righe = lavori.dettaglio(lavoro).getRighe();
        var ctx = org.springframework.security.core.context.SecurityContextHolder.getContext();
        ctx.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "collaboratore@demo.it", null, List.of()));
        try {
            lavori.segnaRiga(righe.get(0).getId(), true);
            var prima = lavori.dettaglio(lavoro).getRighe().get(0);
            assertThat(prima.getCompletataIl()).isNotNull();
            assertThat(prima.getCompletataDa()).isEqualTo(luca);
            // tolta la spunta: niente data; rimessa: data nuova
            lavori.segnaRiga(righe.get(0).getId(), false);
            assertThat(lavori.dettaglio(lavoro).getRighe().get(0).getCompletataIl()).isNull();
            lavori.segnaRiga(righe.get(0).getId(), true);
            // la bici pronta: anche l'intervento non spuntato risulta fatto adesso, da chi l'ha segnata pronta
            lavori.segnaPronto(lavoro);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        lavori.ritira(lavoro);
        var l = lavori.dettaglio(lavoro);
        assertThat(l.getRighe()).allSatisfy(r -> {
            assertThat(r.isCompletata()).isTrue();
            assertThat(r.getCompletataIl()).isNotNull();
            assertThat(r.getCompletataDa()).isEqualTo(luca);
        });
        assertThat(l.getAnnunciatoIl()).isNotNull();
        assertThat(l.getCheckinIl()).isNotNull();
        assertThat(l.getProntoIl()).isNotNull();
        assertThat(l.getChiusoIl()).isNotNull();
    }
}
