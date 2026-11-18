package it.boff.bike.lavoro;

import it.boff.bike.common.ErroreDominio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Foto dei lavori. Si accettano solo immagini JPEG, PNG, WebP o HEIC (verificate leggendo
 * l'intestazione del file, non solo il nome), fino a {@value #MAX_BYTE} byte ciascuna.
 * Il cliente ne può allegare al massimo {@value #MAX_CLIENTE}; il negozio fino a {@value #MAX_TOTALE}.
 */
@Service
@Transactional
public class FotoService {

    public static final int MAX_BYTE = 8 * 1024 * 1024;
    public static final int MAX_CLIENTE = 3;
    public static final int MAX_TOTALE = 10;

    private final FotoLavoroRepository foto;
    private final LavoroRepository lavori;
    private final Clock clock;

    public FotoService(FotoLavoroRepository foto, LavoroRepository lavori, Clock clock) {
        this.foto = foto;
        this.lavori = lavori;
        this.clock = clock;
    }

    /** Il cliente allega una foto a un proprio lavoro aperto. */
    public void aggiungiDelCliente(Long clienteId, Long lavoroId, String nome, byte[] dati) {
        Lavoro l = lavori.dettaglio(lavoroId).orElseThrow(() -> new ErroreDominio("Lavoro non trovato"));
        if (!l.getBicicletta().getProprietario().getId().equals(clienteId)) {
            throw new ErroreDominio("Lavoro non trovato");
        }
        if (!l.getStato().isAperto()) {
            throw new ErroreDominio("Il lavoro è chiuso");
        }
        if (foto.elenco(lavoroId).stream().filter(FotoInfo::dalCliente).count() >= MAX_CLIENTE) {
            throw new ErroreDominio("Puoi allegare al massimo " + MAX_CLIENTE + " foto");
        }
        salva(l, nome, dati, true);
    }

    /** Il negozio allega una foto (es. prima/dopo l'intervento). */
    public void aggiungiDelNegozio(Long lavoroId, String nome, byte[] dati) {
        Lavoro l = lavori.dettaglio(lavoroId).orElseThrow(() -> new ErroreDominio("Lavoro non trovato"));
        salva(l, nome, dati, false);
    }

    @Transactional(readOnly = true)
    public List<FotoInfo> elenco(Long lavoroId) {
        return foto.elenco(lavoroId);
    }

    /** Contenuto di una foto, verificando che appartenga al lavoro indicato. */
    @Transactional(readOnly = true)
    public byte[] contenuto(Long lavoroId, Long fotoId) {
        FotoLavoro f = foto.findById(fotoId).filter(x -> x.getLavoro().getId().equals(lavoroId))
                .orElseThrow(() -> new ErroreDominio("Foto non trovata"));
        return f.getDati().clone();
    }

    public void elimina(Long fotoId) {
        foto.deleteById(fotoId);
    }

    /** Privacy: cancella tutte le foto dei lavori di un cliente. */
    public void eliminaDelCliente(Long clienteId) {
        foto.eliminaDelCliente(clienteId);
    }

    private void salva(Lavoro l, String nome, byte[] dati, boolean dalCliente) {
        if (dati == null || dati.length == 0) {
            throw new ErroreDominio("File vuoto");
        }
        if (dati.length > MAX_BYTE) {
            throw new ErroreDominio("Foto troppo grande (massimo 8 MB)");
        }
        if (foto.countByLavoroId(l.getId()) >= MAX_TOTALE) {
            throw new ErroreDominio("Raggiunto il numero massimo di foto per questo lavoro");
        }
        String tipo = tipoImmagine(dati);
        if (tipo == null) {
            throw new ErroreDominio("Formato non supportato: usa una foto JPEG, PNG, WebP o HEIC");
        }
        String nomePulito = nome == null || nome.isBlank() ? "foto" : nome.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (nomePulito.length() > 200) nomePulito = nomePulito.substring(nomePulito.length() - 200);
        foto.save(new FotoLavoro(l, nomePulito, tipo, dati, dalCliente, OffsetDateTime.now(clock)));
    }

    /** Riconosce il formato dai primi byte; null se non è un'immagine ammessa. */
    static String tipoImmagine(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        if (d.length >= 12 && d[4] == 'f' && d[5] == 't' && d[6] == 'y' && d[7] == 'p') {
            String marca = new String(d, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (marca.startsWith("hei") || marca.startsWith("hev") || marca.equals("mif1") || marca.equals("msf1")) {
                return "image/heic";
            }
        }
        return null;
    }
}
