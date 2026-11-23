package it.boff.bike.lavoro;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import jakarta.persistence.CascadeType;
import org.hibernate.Hibernate;
import org.hibernate.annotations.BatchSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Lavoro di officina su una bicicletta. Le transizioni di stato sono metodi dell'entità
 * e rifiutano i passaggi non ammessi (vedi {@link StatoLavoro}).
 */
@Entity
@Table(name = "lavoro")
public class Lavoro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bicicletta_id", nullable = false, updatable = false)
    private Bicicletta bicicletta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatoLavoro stato;

    @Column(nullable = false)
    private boolean urgente;

    @Column(name = "note_cliente")
    private String noteCliente;

    @Column(name = "note_interne")
    private String noteInterne;

    @Column(name = "data_promessa")
    private LocalDate dataPromessa;

    @Column(name = "fine_prevista")
    private LocalDate finePrevista;

    /** Lavoro annunciato già in calendario: quando il cliente deve portare la bici (null altrimenti). */
    @Column(name = "arrivo_previsto")
    private java.time.LocalDateTime arrivoPrevisto;

    @Column(name = "annunciato_il", nullable = false)
    private OffsetDateTime annunciatoIl;

    @Column(name = "checkin_il")
    private OffsetDateTime checkinIl;

    @Column(name = "pronto_il")
    private OffsetDateTime prontoIl;

    @Column(name = "chiuso_il")
    private OffsetDateTime chiusoIl;

    @Version
    private long versione;

    @OneToMany(mappedBy = "lavoro", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordine, id")
    @BatchSize(size = 50)
    private List<RigaLavoro> righe = new ArrayList<>();

    protected Lavoro() {
    }

    Lavoro(Bicicletta bicicletta, StatoLavoro stato, String noteCliente, OffsetDateTime adesso) {
        this.bicicletta = bicicletta;
        this.stato = stato;
        this.noteCliente = noteCliente;
        this.annunciatoIl = adesso;
        if (stato == StatoLavoro.IN_NEGOZIO) {
            this.checkinIl = adesso;
        }
    }

    // ---- righe ----

    RigaLavoro aggiungiRiga(TipoIntervento tipo, String descrizione, int durataMin) {
        richiediAperto();
        RigaLavoro r = new RigaLavoro(this, tipo, descrizione, durataMin, righe.size() * 10);
        righe.add(r);
        return r;
    }

    void rimuoviRiga(RigaLavoro riga) {
        richiediAperto();
        righe.remove(riga);
    }

    /** Carica le righe finché la sessione è aperta (getRighe() restituisce solo una vista). */
    void caricaRighe() {
        Hibernate.initialize(righe);
    }

    public int minutiTotali() {
        return righe.stream().filter(RigaLavoro::isValida).mapToInt(RigaLavoro::getDurataMin).sum();
    }

    public int minutiDaFare() {
        return righe.stream().filter(r -> r.isValida() && !r.isCompletata()).mapToInt(RigaLavoro::getDurataMin).sum();
    }

    public boolean haApprovazioniInAttesa() {
        return righe.stream().anyMatch(RigaLavoro::isInAttesaDiApprovazione);
    }

    // ---- transizioni ----

    void checkIn(OffsetDateTime adesso) {
        richiedi(Set.of(StatoLavoro.ANNUNCIATO), "fare il check-in");
        stato = StatoLavoro.IN_NEGOZIO;
        checkinIl = adesso;
        arrivoPrevisto = null;
    }

    /** Solo per i lavori annunciati: giorno e ora in cui il cliente deve portare la bici (null = non fissato). */
    void impostaArrivoPrevisto(java.time.LocalDateTime quando) {
        arrivoPrevisto = stato == StatoLavoro.ANNUNCIATO ? quando : null;
    }

    void segnaPianificato(LocalDate fine) {
        finePrevista = fine;
        if (stato == StatoLavoro.IN_NEGOZIO) {
            stato = StatoLavoro.PIANIFICATO;
        }
    }

    void togliPianificazione() {
        finePrevista = null;
        if (stato == StatoLavoro.PIANIFICATO) {
            stato = StatoLavoro.IN_NEGOZIO;
        }
    }

    void inizia() {
        richiedi(Set.of(StatoLavoro.IN_NEGOZIO, StatoLavoro.PIANIFICATO), "iniziare il lavoro");
        stato = StatoLavoro.IN_LAVORAZIONE;
    }

    /** Segna la bici come pronta: tutte le righe risultano completate. */
    void segnaPronto(OffsetDateTime adesso) {
        segnaPronto(adesso, null);
    }

    /** Come sopra; gli interventi non ancora spuntati risultano fatti adesso da {@code chi}. */
    void segnaPronto(OffsetDateTime adesso, Long chi) {
        richiedi(StatoLavoro.IN_OFFICINA, "segnare la bici come pronta");
        if (haApprovazioniInAttesa()) {
            throw new ErroreDominio("C'è un intervento in attesa della risposta del cliente: "
                    + "aspetta la sua risposta o rimuovilo");
        }
        righe.stream().filter(RigaLavoro::isValida).forEach(r -> r.segna(true, adesso, chi));
        stato = StatoLavoro.PRONTO;
        prontoIl = adesso;
    }

    /** Riporta in officina una bici segnata pronta per errore o con un problema. */
    void riapri() {
        richiedi(Set.of(StatoLavoro.PRONTO), "riaprire il lavoro");
        stato = StatoLavoro.IN_LAVORAZIONE;
        prontoIl = null;
    }

    void ritira(OffsetDateTime adesso) {
        richiedi(Set.of(StatoLavoro.PRONTO), "registrare il ritiro");
        stato = StatoLavoro.RITIRATO;
        chiusoIl = adesso;
    }

    void annulla(OffsetDateTime adesso) {
        richiediAperto();
        stato = StatoLavoro.ANNULLATO;
        chiusoIl = adesso;
        finePrevista = null;
    }

    private void richiediAperto() {
        if (!stato.isAperto()) {
            throw new ErroreDominio("Il lavoro è chiuso e non può essere modificato");
        }
    }

    private void richiedi(Set<StatoLavoro> ammessi, String azione) {
        if (!ammessi.contains(stato)) {
            throw new ErroreDominio("Non è possibile " + azione + " per un lavoro nello stato \""
                    + stato.getEtichetta() + "\"");
        }
    }

    // ---- getter / setter semplici ----

    public Long getId() { return id; }
    public Bicicletta getBicicletta() { return bicicletta; }
    public StatoLavoro getStato() { return stato; }
    public boolean isUrgente() { return urgente; }
    public String getNoteCliente() { return noteCliente; }
    public String getNoteInterne() { return noteInterne; }
    public LocalDate getDataPromessa() { return dataPromessa; }
    public LocalDate getFinePrevista() { return finePrevista; }
    public java.time.LocalDateTime getArrivoPrevisto() { return arrivoPrevisto; }
    public OffsetDateTime getAnnunciatoIl() { return annunciatoIl; }
    public OffsetDateTime getCheckinIl() { return checkinIl; }
    public OffsetDateTime getProntoIl() { return prontoIl; }
    public OffsetDateTime getChiusoIl() { return chiusoIl; }
    public long getVersione() { return versione; }
    public List<RigaLavoro> getRighe() { return Collections.unmodifiableList(righe); }

    void setUrgente(boolean urgente) { this.urgente = urgente; }
    void cancellaNoteCliente() { this.noteCliente = null; }
    void setNoteInterne(String noteInterne) { this.noteInterne = noteInterne; }
    void setDataPromessa(LocalDate dataPromessa) { this.dataPromessa = dataPromessa; }
    void setStato(StatoLavoro stato) { this.stato = stato; }
}
