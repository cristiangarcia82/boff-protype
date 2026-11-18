package it.boff.bike.bicicletta;

import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Gestione delle biciclette di un cliente.
 * Ogni operazione è vincolata al proprietario: un cliente non può vedere né
 * modificare le bici di un altro.
 */
@Service
@Validated
@Transactional
public class BiciclettaService {

    private final BiciclettaRepository biciclette;
    private final UtenteRepository utenti;

    public BiciclettaService(BiciclettaRepository biciclette, UtenteRepository utenti) {
        this.biciclette = biciclette;
        this.utenti = utenti;
    }

    @Transactional(readOnly = true)
    public List<Bicicletta> elencoDi(Long proprietarioId) {
        return biciclette.findByProprietarioIdAndArchiviataFalseOrderByMarcaAscModelloAsc(proprietarioId);
    }

    public Bicicletta aggiungi(Long proprietarioId, @Valid DatiBicicletta dati) {
        Utente proprietario = utenti.findById(proprietarioId)
                .filter(u -> u.getRuolo() == Ruolo.CLIENTE)
                .orElseThrow(() -> new IllegalArgumentException("Solo un cliente può possedere biciclette"));
        Bicicletta bici = new Bicicletta(proprietario);
        bici.aggiorna(dati);
        return biciclette.save(bici);
    }

    public Bicicletta modifica(Long proprietarioId, Long biciclettaId, @Valid DatiBicicletta dati) {
        Bicicletta bici = trova(proprietarioId, biciclettaId);
        bici.aggiorna(dati);
        return bici;
    }

    /** Rimuove la bici dall'elenco del cliente, conservandola in archivio. */
    public void rimuovi(Long proprietarioId, Long biciclettaId) {
        trova(proprietarioId, biciclettaId).archivia();
    }

    private Bicicletta trova(Long proprietarioId, Long biciclettaId) {
        return biciclette.findByIdAndProprietarioIdAndArchiviataFalse(biciclettaId, proprietarioId)
                .orElseThrow(BiciclettaNonTrovataException::new);
    }
}
