package it.boff.bike.privacy;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaRepository;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.notifiche.IscrizionePushRepository;
import it.boff.bike.notifiche.TokenPasswordRepository;
import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diritti GDPR del cliente: copia dei propri dati (portabilità) e cancellazione dell'account.
 * La cancellazione <b>anonimizza</b> i dati personali: i lavori restano nello storico del
 * negozio, ma non sono più riconducibili alla persona.
 */
@Service
@Transactional
public class PrivacyService {

    private final UtenteRepository utenti;
    private final BiciclettaRepository biciclette;
    private final BiciclettaService biciclettaService;
    private final LavoroService lavori;
    private final IscrizionePushRepository iscrizioni;
    private final TokenPasswordRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper json;

    public PrivacyService(UtenteRepository utenti, BiciclettaRepository biciclette, BiciclettaService biciclettaService,
                          LavoroService lavori, IscrizionePushRepository iscrizioni, TokenPasswordRepository tokens,
                          PasswordEncoder passwordEncoder, ObjectMapper json) {
        this.utenti = utenti;
        this.biciclette = biciclette;
        this.biciclettaService = biciclettaService;
        this.lavori = lavori;
        this.iscrizioni = iscrizioni;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.json = json;
    }

    /** Tutti i dati del cliente in formato JSON leggibile. */
    @Transactional(readOnly = true)
    public String esporta(Long clienteId) {
        Utente u = cliente(clienteId);
        Map<String, Object> dati = new LinkedHashMap<>();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("nome", u.getNome());
        account.put("cognome", u.getCognome());
        account.put("email", u.getEmail());
        account.put("telefono", u.getTelefono());
        account.put("registratoIl", testo(u.getCreatoIl()));
        account.put("privacyAccettataIl", testo(u.getPrivacyAccettataIl()));
        dati.put("account", account);

        List<Map<String, Object>> bici = new ArrayList<>();
        for (Bicicletta b : biciclette.findByProprietarioIdOrderById(clienteId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("marca", b.getMarca());
            m.put("modello", b.getModello());
            m.put("tipologia", b.getTipologia().getEtichetta());
            m.put("elettrica", b.isElettrica());
            m.put("taglia", b.getTaglia());
            m.put("colore", b.getColore());
            m.put("anno", b.getAnno());
            m.put("numeroTelaio", b.getNumeroTelaio());
            m.put("note", b.getNote());
            m.put("rimossa", b.isArchiviata());
            bici.add(m);
        }
        dati.put("biciclette", bici);

        List<Map<String, Object>> elenco = new ArrayList<>();
        for (Lavoro l : lavori.lavoriDelCliente(clienteId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bici", l.getBicicletta().getNome());
            m.put("stato", l.getStato().getEtichetta());
            m.put("annunciatoIl", testo(l.getAnnunciatoIl()));
            m.put("chiusoIl", testo(l.getChiusoIl()));
            m.put("noteCliente", l.getNoteCliente());
            m.put("interventi", l.getRighe().stream().map(RigaLavoro::getDescrizione).toList());
            elenco.add(m);
        }
        dati.put("lavori", elenco);
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(dati);
    }

    /** Cancella (anonimizza) l'account. Non possibile con un lavoro ancora aperto. */
    public void eliminaAccount(Long clienteId) {
        Utente u = cliente(clienteId);
        boolean apertoInCorso = lavori.lavoriDelCliente(clienteId).stream().anyMatch(l -> l.getStato().isAperto());
        if (apertoInCorso) {
            throw new ErroreDominio("Hai un lavoro ancora aperto: annullalo o attendi il ritiro della bici, "
                    + "poi potrai eliminare l'account.");
        }
        biciclette.findByProprietarioIdOrderById(clienteId).stream().filter(b -> !b.isArchiviata())
                .forEach(b -> biciclettaService.rimuovi(clienteId, b.getId()));
        lavori.cancellaNoteDelCliente(clienteId);
        iscrizioni.deleteByUtenteId(clienteId);
        tokens.deleteByUtenteId(clienteId);
        byte[] casuale = new byte[32];
        new SecureRandom().nextBytes(casuale);
        u.anonimizza(passwordEncoder.encode(HexFormat.of().formatHex(casuale)));
    }

    private Utente cliente(Long id) {
        return utenti.findById(id).filter(u -> u.getRuolo() == Ruolo.CLIENTE)
                .orElseThrow(() -> new ErroreDominio("Funzione disponibile solo per i clienti"));
    }

    private static String testo(Object o) {
        return o == null ? null : o.toString();
    }
}
