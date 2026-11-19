package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.pianificazione.PianificazioneService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Riempie l'officina per i prossimi 7 giorni, qualunque siano giorno, ora e personale presente.
 * Serve un lavoro per persona: una bici non può essere lavorata da due persone nello stesso momento,
 * quindi un solo lavoro enorme non riempie le ore di chi lavora in parallelo.
 */
public final class OfficinaPiena implements AutoCloseable {

    private final LavoroService lavori;
    private final CatalogoService catalogo;
    private final TipoIntervento lungo;
    private final List<Long> creati = new ArrayList<>();

    public OfficinaPiena(Long cliente, BiciclettaService biciclette, LavoroService lavori, CatalogoService catalogo,
                         PianificazioneService pianificazione) {
        this.lavori = lavori;
        this.catalogo = catalogo;
        this.lungo = catalogo.salva(new TipoIntervento("Restauro completo " + System.nanoTime(), 1440));
        long capacita = pianificazione.carico(7).capacita();
        while (pianificazione.carico(7).percento() < 95 && creati.size() < 20) {
            Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Prova", "Carico " + UUID.randomUUID(),
                    Tipologia.STRADA)).getId();
            Long id = lavori.creaAlBanco(bici, List.of(lungo.getId()), null).getId();
            creati.add(id);
            for (long m = 1440; m <= capacita; m += 1440) {
                lavori.aggiungiDaCatalogo(id, lungo.getId());
            }
        }
    }

    @Override
    public void close() {
        creati.forEach(lavori::annulla);
        lungo.setAttivo(false);
        catalogo.salva(lungo);
    }
}
