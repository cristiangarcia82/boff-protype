package it.boff.bike.lavoro;

/** Descrizione di una foto senza il contenuto. */
public record FotoInfo(Long id, String nomeFile, String tipo, int dimensione, boolean dalCliente) {
}
