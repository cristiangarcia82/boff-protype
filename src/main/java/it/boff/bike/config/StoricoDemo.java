package it.boff.bike.config;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.Disponibilita;
import it.boff.bike.calendario.Intervallo;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dati demo per tutto il mese corrente, 3–4 bici al giorno, per vedere il calendario "vissuto":
 * <ul>
 *   <li>giorni passati: lavori svolti e ritirati (gli ultimi pronti, da ritirare), con gli interventi al loro
 *       posto nel calendario. Scritti direttamente nel database perché i servizi non pianificano nel passato;</li>
 *   <li>giorni futuri: lavori fissati in calendario, in negozio nei prossimi due giorni e annunciati
 *       (bici non ancora arrivata) più avanti.</li>
 * </ul>
 * Si creano una volta sola (riconosciuti dalla nota interna {@link #NOTA}).
 */
@Component
class StoricoDemo {

    static final String NOTA = "Dati demo: storico del mese";
    private static final Logger log = LoggerFactory.getLogger(StoricoDemo.class);

    /** Interventi tipici di un giorno qualunque, con il loro peso (più alto = più frequente). */
    private static final List<List<String>> COMBINAZIONI = List.of(
            List.of("Riparazione foratura"),
            List.of("Riparazione foratura", "Regolazione freni"),
            List.of("Regolazione cambio", "Regolazione freni"),
            List.of("Tagliando base (controllo, regolazioni, lubrificazione)"),
            List.of("Tagliando base (controllo, regolazioni, lubrificazione)", "Lavaggio"),
            List.of("Sostituzione catena", "Regolazione cambio"),
            List.of("Sostituzione pastiglie / pattini freno", "Spurgo freni idraulici"),
            List.of("Sostituzione copertone", "Centratura ruota"),
            List.of("Diagnosi e aggiornamento e-bike"),
            List.of("Diagnosi e aggiornamento e-bike", "Regolazione freni"),
            List.of("Sostituzione cavi e guaine", "Regolazione cambio"),
            List.of("Montaggio tubeless"),
            List.of("Sostituzione trasmissione (catena, pacco pignoni, corone)"),
            List.of("Revisione forcella (base)"),
            List.of("Tagliando completo (smontaggio, pulizia, regolazioni)"));

    private static final String[][] CLIENTI = {
            {"Luca", "Zanetti"}, {"Chiara", "Pedrazzini"}, {"Marco", "Crameri"}, {"Elena", "Tognina"},
            {"Andrea", "Lanfranchi"}, {"Francesca", "Raselli"}, {"Davide", "Giuliani"}, {"Martina", "Costa"},
            {"Simone", "Bassi"}, {"Laura", "Fanconi"}, {"Nicola", "Pozzi"}, {"Sofia", "Paganini"},
            {"Matteo", "Lardi"}, {"Giorgia", "Plozza"}, {"Stefano", "Bondolfi"}, {"Alessia", "Menghini"},
            {"Fabio", "Cortesi"}, {"Valentina", "Isepponi"}, {"Riccardo", "Triacca"}, {"Anna", "Beti"},
            {"Pietro", "Lardelli"}, {"Federica", "Compagnoni"}};

    private static final Object[][] BICI = {
            {"Specialized", "Rockhopper", Tipologia.MTB}, {"Scott", "Spark 930", Tipologia.MTB},
            {"Trek", "Domane AL 3", Tipologia.STRADA}, {"Giant", "Revolt", Tipologia.GRAVEL},
            {"Cube", "Kathmandu Hybrid", Tipologia.EBIKE}, {"Bianchi", "Spillo", Tipologia.CITTA},
            {"Orbea", "Rise", Tipologia.EBIKE}, {"Canyon", "Grail 6", Tipologia.GRAVEL},
            {"Focus", "Jam²", Tipologia.EBIKE}, {"Merida", "Big.Nine", Tipologia.MTB},
            {"Wilier", "Rave SLR", Tipologia.GRAVEL}, {"Riese & Müller", "Charger4", Tipologia.EBIKE},
            {"Kalkhoff", "Endeavour", Tipologia.EBIKE}, {"Colnago", "V3", Tipologia.STRADA},
            {"Atala", "B-Easy", Tipologia.EBIKE}, {"Thömus", "Lightrider", Tipologia.EBIKE}};

    private final JdbcTemplate jdbc;
    private final CalendarioService calendario;
    private final CatalogoService catalogo;
    private final BiciclettaService biciclette;
    private final LavoroService lavori;
    private final PianificazioneService pianificazione;
    private final UtenteRepository utenti;
    private final PasswordEncoder passwordEncoder;

    StoricoDemo(JdbcTemplate jdbc, CalendarioService calendario, CatalogoService catalogo, BiciclettaService biciclette,
                LavoroService lavori, PianificazioneService pianificazione, UtenteRepository utenti,
                PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.calendario = calendario;
        this.catalogo = catalogo;
        this.biciclette = biciclette;
        this.lavori = lavori;
        this.pianificazione = pianificazione;
        this.utenti = utenti;
        this.passwordEncoder = passwordEncoder;
    }

    void crea(List<Utente> personale, ZoneId zona) {
        Integer esistenti = jdbc.queryForObject("SELECT count(*) FROM lavoro WHERE note_interne = ?", Integer.class, NOTA);
        if (esistenti != null && esistenti > 0) return;

        Random caso = new Random(2026);
        Map<String, TipoIntervento> tipi = catalogo.attivi().stream()
                .collect(Collectors.toMap(TipoIntervento::getNome, t -> t, (a, b) -> a));
        List<Bicicletta> parco = parcoBici(caso);
        Set<Long> conLavoroAperto = new HashSet<>(jdbc.queryForList(
                "SELECT bicicletta_id FROM lavoro WHERE stato NOT IN ('RITIRATO', 'ANNULLATO')", Long.class));

        LocalDate oggi = calendario.oggi();
        LocalDate primo = oggi.withDayOfMonth(1);
        LocalDate ultimo = oggi.withDayOfMonth(oggi.lengthOfMonth());
        Disponibilita disp = calendario.disponibilita(primo, ultimo);
        int passati = 0, futuri = 0;

        for (LocalDate g = primo; !g.isAfter(ultimo); g = g.plusDays(1)) {
            LocalDate giorno = g;
            List<Utente> alLavoro = personale.stream().filter(p -> !disp.officina(p.getId(), giorno).isEmpty()).toList();
            if (alLavoro.isEmpty()) continue;
            int quante = 3 + caso.nextInt(2);
            if (g.isBefore(oggi)) {
                // dove arriva ciascuno nella giornata
                Map<Long, LocalTime> cursore = new java.util.HashMap<>();
                for (int i = 0; i < quante; i++) {
                    Utente chi = alLavoro.get(i % alLavoro.size());
                    List<String> combinazione = COMBINAZIONI.get(caso.nextInt(COMBINAZIONI.size()));
                    if (passato(g, chi, combinazione, tipi, disp, cursore, parco, conLavoroAperto, caso, oggi, zona)) passati++;
                }
            } else if (g.isAfter(oggi)) {
                for (int i = 0; i < quante; i++) {
                    Utente chi = alLavoro.get(i % alLavoro.size());
                    List<String> combinazione = COMBINAZIONI.get(caso.nextInt(COMBINAZIONI.size() - 1)); // niente tagliandi completi
                    if (futuro(g, chi, combinazione, tipi, parco, conLavoroAperto, caso, oggi)) futuri++;
                }
            }
        }
        log.info("Dati demo del mese: {} lavori passati, {} lavori fissati nei prossimi giorni", passati, futuri);
    }

    /** Un lavoro già svolto in un giorno passato: interventi uno dopo l'altro nell'orario di lavoro di chi lo fa. */
    private boolean passato(LocalDate g, Utente chi, List<String> combinazione, Map<String, TipoIntervento> tipi,
                            Disponibilita disp, Map<Long, LocalTime> cursore, List<Bicicletta> parco,
                            Set<Long> conLavoroAperto, Random caso, LocalDate oggi, ZoneId zona) {
        List<Object[]> righe = new ArrayList<>();
        LocalTime da = cursore.getOrDefault(chi.getId(), LocalTime.MIN);
        for (String nome : combinazione) {
            TipoIntervento t = tipi.get(nome);
            if (t == null) continue;
            int durata = t.getDurataStandardMin();
            Intervallo posto = null;
            for (Intervallo f : disp.officina(chi.getId(), g)) {
                LocalTime inizio = f.inizio().isBefore(da) ? da : f.inizio();
                LocalTime fine = inizio.plusMinutes(durata);
                if (fine.isAfter(inizio) && !fine.isAfter(f.fine())) {
                    posto = new Intervallo(inizio, fine);
                    break;
                }
            }
            if (posto == null) return false;
            righe.add(new Object[] {t, posto});
            da = posto.fine();
        }
        if (righe.isEmpty()) return false;
        cursore.put(chi.getId(), da.plusMinutes(caso.nextInt(3) * 15L)); // un po' di respiro tra un lavoro e l'altro

        Intervallo prima = (Intervallo) righe.get(0)[1];
        Intervallo dopo = (Intervallo) righe.get(righe.size() - 1)[1];
        LocalDateTime ritiro = g.plusDays(1 + caso.nextInt(3)).atTime(16 + caso.nextInt(3), caso.nextBoolean() ? 0 : 30);
        boolean ritirata = ritiro.toLocalDate().isBefore(oggi);
        Bicicletta bici = scegli(parco, conLavoroAperto, caso, !ritirata);
        if (bici == null) return false;

        LocalDateTime arrivo = LocalDateTime.of(g, prima.inizio()).minusMinutes(15 + caso.nextInt(4) * 15L);
        Long lavoro = jdbc.queryForObject("""
                INSERT INTO lavoro (bicicletta_id, stato, urgente, note_interne, fine_prevista,
                                    annunciato_il, checkin_il, pronto_il, chiuso_il)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, bici.getId(), ritirata ? "RITIRATO" : "PRONTO", caso.nextInt(8) == 0, NOTA, g,
                ts(arrivo.minusDays(1 + caso.nextInt(3)).withHour(19), zona), ts(arrivo, zona),
                ts(LocalDateTime.of(g, dopo.fine()), zona), ritirata ? ts(ritiro, zona) : null);
        int ordine = 0;
        for (Object[] r : righe) {
            TipoIntervento t = (TipoIntervento) r[0];
            Intervallo posto = (Intervallo) r[1];
            int effettivi = Math.max(5, t.getDurataStandardMin() + (caso.nextInt(5) - 2) * 5);
            Long riga = jdbc.queryForObject("""
                    INSERT INTO riga_lavoro (lavoro_id, tipo_intervento_id, descrizione, durata_min, completata, ordine,
                                             minuti_effettivi)
                    VALUES (?, ?, ?, ?, TRUE, ?, ?) RETURNING id
                    """, Long.class, lavoro, t.getId(), t.getNome(), t.getDurataStandardMin(), ordine++, effettivi);
            jdbc.update("""
                    INSERT INTO assegnazione (riga_lavoro_id, utente_id, data, ora_inizio, ora_fine, bloccata)
                    VALUES (?, ?, ?, ?, ?, FALSE)
                    """, riga, chi.getId(), g, posto.inizio(), posto.fine());
        }
        return true;
    }

    /** Un lavoro fissato in un giorno futuro: in negozio se è vicino, altrimenti annunciato (bici non arrivata). */
    private boolean futuro(LocalDate g, Utente chi, List<String> combinazione, Map<String, TipoIntervento> tipi,
                           List<Bicicletta> parco, Set<Long> conLavoroAperto, Random caso, LocalDate oggi) {
        List<Long> idTipi = combinazione.stream().map(tipi::get).filter(java.util.Objects::nonNull).map(TipoIntervento::getId).toList();
        Bicicletta bici = scegli(parco, conLavoroAperto, caso, true);
        if (idTipi.isEmpty() || bici == null) return false;
        boolean inNegozio = !g.isAfter(oggi.plusDays(2));
        Long id = inNegozio ? lavori.creaAlBanco(bici.getId(), idTipi, null).getId()
                : lavori.annuncia(bici.getProprietario().getId(), bici.getId(), idTipi, null).getId();
        lavori.impostaDettagli(id, false, null, NOTA);
        try {
            pianificazione.fissaLavoro(id, chi.getId(), g, null);
            return true;
        } catch (ErroreDominio e) {
            lavori.annulla(id); // quel giorno non c'è posto: niente lavoro demo
            return false;
        }
    }

    /** Una bici del parco; per un lavoro che resta aperto, una che non ne ha già uno. */
    private static Bicicletta scegli(List<Bicicletta> parco, Set<Long> conLavoroAperto, Random caso, boolean restaAperto) {
        for (int tentativi = 0; tentativi < parco.size() * 2; tentativi++) {
            Bicicletta b = parco.get(caso.nextInt(parco.size()));
            if (!restaAperto) return b;
            if (conLavoroAperto.add(b.getId())) return b;
        }
        return null;
    }

    /** Clienti e bici di esempio (si creano solo la prima volta). */
    private List<Bicicletta> parcoBici(Random caso) {
        List<Bicicletta> parco = new ArrayList<>();
        int n = 0;
        for (String[] c : CLIENTI) {
            String email = (c[0] + "." + c[1]).toLowerCase() + "@demo.it";
            Utente u = utenti.findByEmail(email).orElseGet(() -> {
                Utente nuovo = new Utente(email, passwordEncoder.encode(DatiIniziali.PASSWORD_DEMO), c[0], c[1], Ruolo.CLIENTE, false);
                nuovo.setTelefono("+41 79 " + (100 + caso.nextInt(900)) + " " + (10 + caso.nextInt(90)) + " " + (10 + caso.nextInt(90)));
                return utenti.save(nuovo);
            });
            List<Bicicletta> sue = biciclette.elencoDi(u.getId());
            if (sue.isEmpty()) {
                int quante = n % 3 == 0 ? 2 : 1;
                for (int i = 0; i < quante; i++) {
                    Object[] b = BICI[(n + i * 7) % BICI.length];
                    sue = new ArrayList<>(sue);
                    sue.add(biciclette.aggiungi(u.getId(), DatiBicicletta.di((String) b[0], (String) b[1], (Tipologia) b[2])));
                }
            }
            parco.addAll(sue);
            n++;
        }
        return parco;
    }

    private static Timestamp ts(LocalDateTime t, ZoneId zona) {
        return Timestamp.from(t.atZone(zona).toInstant());
    }
}
