package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.pianificazione.Assegnazione;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PianificazioneServiceIT {

    @Autowired PianificazioneService pianificazione;
    @Autowired LavoroService lavori;
    @Autowired UtenteService utenti;
    @Autowired BiciclettaService biciclette;
    @Autowired CatalogoService catalogo;
    @Autowired CalendarioService calendario;

    Long bici;
    TipoIntervento catena;

    @BeforeEach
    void prepara() {
        Long cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Trek", "Domane", Tipologia.STRADA)).getId();
        catena = catalogo.attivi().stream().filter(t -> t.getNome().equals("Sostituzione catena")).findFirst().orElseThrow();
    }

    private int minutiInCalendario(Long lavoroId) {
        return pianificazione.diLavoro(lavoroId).stream().mapToInt(a -> (int) a.intervallo().minuti()).sum();
    }

    @Test
    void unLavoroInNegozioVienePianificatoDaSolo() {
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        Lavoro l = lavori.dettaglio(id);
        assertThat(l.getStato()).isEqualTo(StatoLavoro.PIANIFICATO);
        assertThat(l.getFinePrevista()).isNotNull().isAfterOrEqualTo(calendario.oggi());
        assertThat(minutiInCalendario(id)).isEqualTo(catena.getDurataStandardMin());

        // aggiungere un intervento ripianifica
        lavori.aggiungiLibera(id, "Controllo raggi", 40);
        assertThat(minutiInCalendario(id)).isEqualTo(catena.getDurataStandardMin() + 40);

        // bici pronta: esce dal calendario
        lavori.segnaPronto(id);
        assertThat(pianificazione.diLavoro(id)).isEmpty();
    }

    @Test
    void unLavoroSoloAnnunciatoNonVaInCalendario() {
        Long cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        Long b = biciclette.aggiungi(cliente, DatiBicicletta.di("Atala", "City", Tipologia.CITTA)).getId();
        Long id = lavori.annuncia(cliente, b, List.of(catena.getId()), null).getId();
        assertThat(pianificazione.diLavoro(id)).isEmpty();
        lavori.checkIn(id);
        assertThat(minutiInCalendario(id)).isEqualTo(catena.getDurataStandardMin());
    }

    @Test
    void spostamentoManualeBloccaLAssegnazione() {
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        Assegnazione a = pianificazione.diLavoro(id).get(0);

        // trova un giorno futuro (non oggi) in cui qualcuno ha ore di officina
        LocalDate da = calendario.oggi().plusDays(1);
        Disponibilita d = calendario.disponibilita(da, da.plusDays(21));
        LocalDate giorno = null;
        Long persona = null;
        for (int i = 0; i < 21 && giorno == null; i++) {
            for (Long p : d.persone()) {
                if (d.officina(p, da.plusDays(i)).stream().mapToLong(x -> x.minuti()).sum() >= 120) {
                    giorno = da.plusDays(i);
                    persona = p;
                    break;
                }
            }
        }
        assertThat(giorno).as("serve un giorno con ore di officina nei dati demo").isNotNull();

        pianificazione.sposta(a.getId(), persona, giorno);
        Assegnazione spostata = pianificazione.diLavoro(id).get(0);
        assertThat(spostata.isBloccata()).isTrue();
        assertThat(spostata.getData()).isEqualTo(giorno);
        assertThat(spostata.getUtente().getId()).isEqualTo(persona);
        assertThat(lavori.dettaglio(id).getFinePrevista()).isEqualTo(giorno);

        // una ripianificazione non la sposta
        pianificazione.ripianifica();
        assertThat(pianificazione.diLavoro(id)).singleElement()
                .satisfies(x -> assertThat(x.getData()).isEqualTo(spostata.getData()));

        assertThatThrownBy(() -> pianificazione.sposta(spostata.getId(), spostata.getUtente().getId(),
                calendario.oggi().minusDays(1))).isInstanceOf(ErroreDominio.class);
    }

    @Test
    void unaSolaRipianificazionePerAzione() {
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        Long riga = lavori.dettaglio(id).getRighe().get(0).getId();
        long prima = pianificazione.ricalcoliEseguiti();
        // segnare un intervento fatto cambia anche lo stato (in lavorazione): un solo ricalcolo
        lavori.segnaRiga(riga, true);
        assertThat(pianificazione.ricalcoliEseguiti() - prima).isEqualTo(1);
    }

    @Test
    void ilBloccoDiUnaBiciSiSpostaTuttoInsiemeAllaSettimanaDopo() {
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        lavori.aggiungiLibera(id, "Controllo raggi", 40);
        List<Long> ids = pianificazione.diLavoro(id).stream()
                .sorted(java.util.Comparator.comparing(Assegnazione::getData).thenComparing(Assegnazione::getOraInizio))
                .map(Assegnazione::getId).toList();
        long totale = catena.getDurataStandardMin() + 40;

        // un giorno della settimana dopo con uno spazio di officina che contiene tutto il blocco
        LocalDate da = calendario.oggi().plusDays(7);
        Disponibilita d = calendario.disponibilita(da, da.plusDays(14));
        LocalDate giorno = null;
        Long persona = null;
        for (int i = 0; i < 14 && giorno == null; i++) {
            for (Long p : d.persone()) {
                if (d.officina(p, da.plusDays(i)).stream().anyMatch(x -> x.minuti() >= totale)) {
                    giorno = da.plusDays(i);
                    persona = p;
                    break;
                }
            }
        }
        assertThat(giorno).as("serve un giorno con ore di officina nei dati demo").isNotNull();

        pianificazione.spostaTutti(ids, persona, giorno, null);
        List<Assegnazione> dopo = pianificazione.diLavoro(id).stream()
                .sorted(java.util.Comparator.comparing(Assegnazione::getOraInizio)).toList();
        Long chi = persona;
        LocalDate g = giorno;
        assertThat(dopo).hasSameSizeAs(ids).allSatisfy(a -> {
            assertThat(a.isBloccata()).isTrue();
            assertThat(a.getUtente().getId()).isEqualTo(chi);
            assertThat(a.getData()).isEqualTo(g);
        });
        assertThat(dopo.stream().mapToLong(a -> a.intervallo().minuti()).sum()).isEqualTo(totale);
        for (int i = 1; i < dopo.size(); i++) {
            assertThat(dopo.get(i).getOraInizio()).as("uno dopo l'altro").isEqualTo(dopo.get(i - 1).getOraFine());
        }
    }

    @Test
    void seUnInterventoNonCiStaNonSiSpostaNiente() {
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        lavori.aggiungiLibera(id, "Controllo raggi", 40);
        List<Assegnazione> prima = pianificazione.diLavoro(id);
        List<Long> ids = prima.stream().map(Assegnazione::getId).toList();
        // un giorno in cui la persona non ha officina (domenica: i dati demo non la prevedono)
        LocalDate domenica = calendario.oggi().plusWeeks(1).with(java.time.DayOfWeek.SUNDAY);
        Long persona = prima.get(0).getUtente().getId();
        assertThatThrownBy(() -> pianificazione.spostaTutti(ids, persona, domenica, null))
                .isInstanceOf(ErroreDominio.class);
        assertThat(pianificazione.diLavoro(id)).noneMatch(Assegnazione::isBloccata)
                .noneMatch(a -> a.getData().equals(domenica));
    }

    @Test
    void senzaRicalcoloAutomaticoCioCheEInCalendarioNonSiSposta() {
        Long primo = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        List<String> prima = pianificazione.diLavoro(primo).stream()
                .map(a -> a.getUtente().getId() + "|" + a.getData() + "|" + a.getOraInizio()).toList();

        // un lavoro urgente arrivato dopo: con il ricalcolo automatico passerebbe davanti
        Long cliente2 = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        Long bici2 = biciclette.aggiungi(cliente2, DatiBicicletta.di("Bianchi", "Oltre", Tipologia.STRADA)).getId();
        Long urgente = lavori.creaAlBanco(bici2, List.of(catena.getId()), null).getId();
        lavori.impostaDettagli(urgente, true, null, null);

        assertThat(pianificazione.diLavoro(primo).stream()
                .map(a -> a.getUtente().getId() + "|" + a.getData() + "|" + a.getOraInizio()).toList())
                .as("il primo lavoro resta dov'era").isEqualTo(prima);
        assertThat(minutiInCalendario(urgente)).as("il nuovo va comunque in calendario")
                .isEqualTo(catena.getDurataStandardMin());

        // un intervento in più trova posto senza spostare il resto; uno fatto libera il suo spazio
        lavori.aggiungiLibera(primo, "Controllo raggi", 40);
        assertThat(minutiInCalendario(primo)).isEqualTo(catena.getDurataStandardMin() + 40);
        Long rigaCatena = lavori.dettaglio(primo).getRighe().get(0).getId();
        lavori.segnaRiga(rigaCatena, true);
        assertThat(minutiInCalendario(primo)).isEqualTo(40);
    }
}
