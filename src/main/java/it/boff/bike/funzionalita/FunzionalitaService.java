package it.boff.bike.funzionalita;

import it.boff.bike.common.ErroreDominio;
import it.boff.bike.notifiche.Impostazione;
import it.boff.bike.notifiche.ImpostazioneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interruttori e parametri delle funzionalità opzionali, salvati nel database.
 * Tutto è spento di default: niente cambia per negozio e clienti finché il titolare non attiva.
 */
@Service
@Transactional
public class FunzionalitaService {

    static final String MESI_PROMEMORIA = "funzione.promemoria.mesi";
    static final String SOGLIA_CARICO = "funzione.avviso-carico.soglia";
    public static final int MESI_PROMEMORIA_DEFAULT = 12;
    public static final int SOGLIA_CARICO_DEFAULT = 85;

    private final ImpostazioneRepository impostazioni;

    public FunzionalitaService(ImpostazioneRepository impostazioni) {
        this.impostazioni = impostazioni;
    }

    @Transactional(readOnly = true)
    public boolean attiva(Funzionalita f) {
        return impostazioni.findById(f.chiave()).map(i -> "true".equals(i.getValore())).orElse(false);
    }

    public void imposta(Funzionalita f, boolean attiva) {
        impostazioni.save(new Impostazione(f.chiave(), String.valueOf(attiva)));
    }

    /** Mesi dall'ultimo ritiro dopo cui si invia il promemoria (1–36). */
    @Transactional(readOnly = true)
    public int mesiPromemoria() {
        return intero(MESI_PROMEMORIA, MESI_PROMEMORIA_DEFAULT);
    }

    public void impostaMesiPromemoria(int mesi) {
        if (mesi < 1 || mesi > 36) throw new ErroreDominio("Indica un numero di mesi tra 1 e 36");
        impostazioni.save(new Impostazione(MESI_PROMEMORIA, String.valueOf(mesi)));
    }

    /** Percentuale di occupazione dell'officina oltre cui si avvisano i clienti (50–100). */
    @Transactional(readOnly = true)
    public int sogliaCarico() {
        return intero(SOGLIA_CARICO, SOGLIA_CARICO_DEFAULT);
    }

    public void impostaSogliaCarico(int percento) {
        if (percento < 50 || percento > 100) throw new ErroreDominio("Indica una soglia tra 50% e 100%");
        impostazioni.save(new Impostazione(SOGLIA_CARICO, String.valueOf(percento)));
    }

    private int intero(String chiave, int predefinito) {
        return impostazioni.findById(chiave).map(i -> {
            try {
                return Integer.parseInt(i.getValore());
            } catch (NumberFormatException e) {
                return predefinito;
            }
        }).orElse(predefinito);
    }
}
