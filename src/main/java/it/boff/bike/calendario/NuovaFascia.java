package it.boff.bike.calendario;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Fascia dell'orario settimanale come inserita nella schermata "Orari". */
public record NuovaFascia(DayOfWeek giorno, LocalTime inizio, LocalTime fine, TipoFascia tipo) {
}
