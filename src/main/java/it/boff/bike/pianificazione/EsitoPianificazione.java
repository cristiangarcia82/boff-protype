package it.boff.bike.pianificazione;

import java.util.List;

/**
 * @param nonPianificabili lavori che non trovano spazio nei prossimi 90 giorni (es. nessuna ora di officina)
 * @param inRitardo        lavori la cui fine prevista supera la data promessa al cliente
 */
public record EsitoPianificazione(List<Long> nonPianificabili, List<Long> inRitardo) {
}
