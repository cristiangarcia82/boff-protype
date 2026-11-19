package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.calendario.Intervallo;
import it.boff.bike.calendario.MotivoAssenza;
import it.boff.bike.calendario.NuovaFascia;
import it.boff.bike.calendario.TipoFascia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.pianificazione.Assegnazione;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class Fase7IT {

    @Autowired CalendarioService calendario;
    @Autowired UtenteService utenti;
    @Autowired PianificazioneService pianificazione;
    @Autowired LavoroService lavori;
    @Autowired BiciclettaService biciclette;
    @Autowired CatalogoService catalogo;

    static final LocalDate MERCOLEDI = LocalDate.of(2032, 3, 10);

    @Test
    void assenzaDiParteDellaGiornata() {
        assertThat(MERCOLEDI.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        Long p = utenti.creaCollaboratore(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        calendario.salvaOrario(p, List.of(
                new NuovaFascia(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 30), TipoFascia.MANUTENZIONE),
                new NuovaFascia(DayOfWeek.WEDNESDAY, LocalTime.of(15, 0), LocalTime.of(19, 0), TipoFascia.MANUTENZIONE)));
        calendario.aggiungiAssenza(p, MERCOLEDI, MERCOLEDI, LocalTime.of(11, 0), LocalTime.of(19, 0),
                MotivoAssenza.ALTRO, "Visita medica");

        Disponibilita d = calendario.disponibilita(MERCOLEDI, MERCOLEDI);
        assertThat(d.assenza(p, MERCOLEDI)).as("non è una giornata intera").isEmpty();
        assertThat(d.officina(p, MERCOLEDI)).containsExactly(new Intervallo(LocalTime.of(9, 0), LocalTime.of(11, 0)));
        assertThat(d.minuti(p, MERCOLEDI, TipoFascia.MANUTENZIONE)).isEqualTo(120);

        assertThatThrownBy(() -> calendario.aggiungiAssenza(p, MERCOLEDI, MERCOLEDI.plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), MotivoAssenza.ALTRO, null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("un solo giorno");
    }

    @Test
    void spostamentoAUnOrarioPreciso() {
        Long cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Wilier", "Filante", Tipologia.STRADA)).getId();
        var catena = catalogo.attivi().stream().filter(t -> t.getNome().equals("Sostituzione catena")).findFirst().orElseThrow();
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        Assegnazione a = pianificazione.diLavoro(id).get(0);

        // il titolare demo è in officina il martedì 9:00–12:30: la settimana prossima, alle 11:00
        Long mario = utenti.personale().stream().filter(u -> u.isTitolare() && u.getEmail().equals("titolare@demo.it"))
                .findFirst().orElseThrow().getId();
        LocalDate martedi = calendario.oggi().plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY));
        if (!calendario.disponibilita(martedi, martedi).chiusura(martedi).isEmpty()) {
            martedi = martedi.plusWeeks(1); // evita le festività
        }
        // con l'officina già piena (altri test) l'intervento può essere diviso in più pezzi: si sposta tutto, come nel calendario
        List<Long> pezzi = pianificazione.diLavoro(id).stream().map(Assegnazione::getId).toList();
        pianificazione.spostaTutti(pezzi, mario, martedi, LocalTime.of(11, 0));
        List<Assegnazione> spostate = pianificazione.diLavoro(id);
        Assegnazione spostata = spostate.get(0);
        assertThat(spostate).allSatisfy(x -> {
            assertThat(x.isBloccata()).isTrue();
            assertThat(x.getData()).isEqualTo(spostata.getData());
        });
        assertThat(spostata.getData()).isEqualTo(martedi);
        assertThat(spostata.getOraInizio()).isEqualTo(LocalTime.of(11, 0));
        assertThat(spostate.stream().mapToLong(x -> x.intervallo().minuti()).sum()).isEqualTo((long) catena.getDurataStandardMin());

        LocalDate stessoGiorno = martedi;
        List<Long> ancora = spostate.stream().map(Assegnazione::getId).toList();
        // alle 12:20 l'intervento (20 min) sforerebbe la fine della fascia (12:30)
        assertThatThrownBy(() -> pianificazione.spostaTutti(ancora, mario, stessoGiorno, LocalTime.of(12, 20)))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("minuti liberi");
    }
}
