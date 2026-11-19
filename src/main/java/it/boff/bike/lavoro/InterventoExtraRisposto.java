package it.boff.bike.lavoro;

/** Il cliente ha risposto alla proposta di un intervento in più. */
public record InterventoExtraRisposto(Long lavoroId, Long rigaId, boolean approvato) {
}
