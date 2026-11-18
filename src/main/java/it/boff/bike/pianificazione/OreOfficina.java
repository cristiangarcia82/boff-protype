package it.boff.bike.pianificazione;

import it.boff.bike.calendario.Intervallo;

import java.time.LocalDate;
import java.util.List;

/** Le ore di officina disponibili: ciò che serve al pianificatore (implementata da Disponibilita). */
public interface OreOfficina {

    /** Persone che possono lavorare in officina, in ordine di preferenza. */
    List<Long> persone();

    /** Fasce di officina di una persona in un giorno (vuote se assente o negozio chiuso). */
    List<Intervallo> officina(Long utenteId, LocalDate giorno);
}
