package it.boff.bike;

import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.calendario.MotivoAssenza;
import it.boff.bike.calendario.NuovaFascia;
import it.boff.bike.calendario.TipoFascia;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class CalendarioServiceIT {

    @Autowired CalendarioService service;
    @Autowired UtenteService utenti;

    Long persona;
    // un martedì lontano, senza festività, che nessun altro test usa
    static final LocalDate MARTEDI = LocalDate.of(2031, 3, 11);

    static NuovaFascia f(DayOfWeek g, String da, String a, TipoFascia t) {
        return new NuovaFascia(g, LocalTime.parse(da), LocalTime.parse(a), t);
    }

    @BeforeEach
    void prepara() {
        persona = utenti.creaCollaboratore(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        service.salvaOrario(persona, List.of(
                f(TUESDAY, "09:00", "12:30", TipoFascia.MANUTENZIONE),
                f(TUESDAY, "15:00", "19:30", TipoFascia.VENDITA)));
    }

    @Test
    void disponibilitaDiUnaGiornataNormale() {
        assertThat(MARTEDI.getDayOfWeek()).isEqualTo(TUESDAY);
        Disponibilita d = service.disponibilita(MARTEDI, MARTEDI);
        assertThat(d.minuti(persona, MARTEDI, TipoFascia.MANUTENZIONE)).isEqualTo(210);
        assertThat(d.minuti(persona, MARTEDI, TipoFascia.VENDITA)).isEqualTo(270);
        assertThat(d.fasce(persona, MARTEDI.plusDays(1))).isEmpty();
        assertThat(d.personale()).extracting(u -> u.getId()).contains(persona);
    }

    @Test
    void lAssenzaAzzeraLaGiornata() {
        service.aggiungiAssenza(persona, MARTEDI, MARTEDI, MotivoAssenza.RIPOSO, null);
        Disponibilita d = service.disponibilita(MARTEDI, MARTEDI);
        assertThat(d.fasce(persona, MARTEDI)).isEmpty();
        assertThat(d.assenza(persona, MARTEDI)).isPresent();
        assertThatThrownBy(() -> service.aggiungiAssenza(persona, MARTEDI.minusDays(2), MARTEDI, MotivoAssenza.FERIE, null))
                .isInstanceOf(ErroreDominio.class);
    }

    @Test
    void chiusureEFestivitaChiudonoIlNegozio() {
        LocalDate settimanaDopo = MARTEDI.plusWeeks(1);
        service.aggiungiChiusura(settimanaDopo, "Inventario");
        Disponibilita d = service.disponibilita(settimanaDopo, settimanaDopo);
        assertThat(d.chiusura(settimanaDopo)).contains("Inventario");
        assertThat(d.fasce(persona, settimanaDopo)).isEmpty();
        assertThatThrownBy(() -> service.aggiungiChiusura(settimanaDopo, "Doppione")).isInstanceOf(ErroreDominio.class);

        LocalDate natale2029 = LocalDate.of(2029, 12, 25); // martedì
        assertThat(service.disponibilita(natale2029, natale2029).chiusura(natale2029)).contains("Natale");
        assertThat(service.giorniChiusi(LocalDate.of(2029, 12, 20), LocalDate.of(2029, 12, 31)))
                .extracting(CalendarioService.GiornoChiuso::descrizione).containsExactly("Natale", "Santo Stefano");
    }

    @Test
    void fasceSovrappostePerLoStessoGiornoRifiutate() {
        assertThatThrownBy(() -> service.salvaOrario(persona, List.of(
                f(MONDAY, "09:00", "13:00", TipoFascia.VENDITA),
                f(MONDAY, "12:00", "14:00", TipoFascia.MANUTENZIONE))))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("lunedì");
        // l'orario precedente resta invariato
        assertThat(service.orarioDi(persona)).hasSize(2);
    }
}
