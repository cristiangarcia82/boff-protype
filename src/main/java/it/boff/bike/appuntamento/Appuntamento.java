package it.boff.bike.appuntamento;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.calendario.Intervallo;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.utente.Utente;
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
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** Un appuntamento di consulenza: un cliente, una persona del negozio, un orario nelle ore di vendita. */
@Entity
@Table(name = "appuntamento")
public class Appuntamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, updatable = false)
    private Utente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoAppuntamento tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bicicletta_id")
    private Bicicletta bicicletta;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "ora_inizio", nullable = false)
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    private LocalTime oraFine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatoAppuntamento stato;

    @Column(name = "note_cliente")
    private String noteCliente;

    @Column(name = "note_interne")
    private String noteInterne;

    @Column(name = "richiesto_dal_cliente", nullable = false, updatable = false)
    private boolean richiestoDalCliente;

    @Column(name = "creato_il", nullable = false, updatable = false)
    private OffsetDateTime creatoIl;

    @Column(name = "aggiornato_il", nullable = false)
    private OffsetDateTime aggiornatoIl;

    protected Appuntamento() {
    }

    Appuntamento(Utente cliente, TipoAppuntamento tipo, Utente utente, Bicicletta bicicletta, LocalDate data,
                 Intervallo orario, String noteCliente, boolean richiestoDalCliente, OffsetDateTime adesso) {
        this.cliente = cliente;
        this.tipo = tipo;
        this.utente = utente;
        this.bicicletta = bicicletta;
        this.data = data;
        this.oraInizio = orario.inizio();
        this.oraFine = orario.fine();
        this.noteCliente = noteCliente;
        this.richiestoDalCliente = richiestoDalCliente;
        this.stato = richiestoDalCliente ? StatoAppuntamento.RICHIESTO : StatoAppuntamento.CONFERMATO;
        this.creatoIl = adesso;
        this.aggiornatoIl = adesso;
    }

    public Intervallo intervallo() {
        return new Intervallo(oraInizio, oraFine);
    }

    public boolean isAttivo() {
        return StatoAppuntamento.ATTIVI.contains(stato);
    }

    void conferma(OffsetDateTime adesso) {
        if (stato != StatoAppuntamento.RICHIESTO) throw new ErroreDominio("L'appuntamento non è da confermare");
        stato = StatoAppuntamento.CONFERMATO;
        aggiornatoIl = adesso;
    }

    void sposta(Utente persona, LocalDate giorno, Intervallo orario, OffsetDateTime adesso) {
        if (!isAttivo()) throw new ErroreDominio("L'appuntamento è " + stato.getEtichetta().toLowerCase());
        this.utente = persona;
        this.data = giorno;
        this.oraInizio = orario.inizio();
        this.oraFine = orario.fine();
        // spostato dal negozio: vale come confermato
        this.stato = StatoAppuntamento.CONFERMATO;
        this.aggiornatoIl = adesso;
    }

    void annulla(OffsetDateTime adesso) {
        if (!isAttivo()) throw new ErroreDominio("L'appuntamento è già " + stato.getEtichetta().toLowerCase());
        stato = StatoAppuntamento.ANNULLATO;
        aggiornatoIl = adesso;
    }

    void segnaSvolto(OffsetDateTime adesso) {
        if (!isAttivo()) throw new ErroreDominio("L'appuntamento è " + stato.getEtichetta().toLowerCase());
        stato = StatoAppuntamento.SVOLTO;
        aggiornatoIl = adesso;
    }

    void setNoteInterne(String note) { this.noteInterne = note; }
    void cancellaNoteCliente() { this.noteCliente = null; this.bicicletta = null; }

    public Long getId() { return id; }
    public Utente getCliente() { return cliente; }
    public TipoAppuntamento getTipo() { return tipo; }
    public Utente getUtente() { return utente; }
    public Bicicletta getBicicletta() { return bicicletta; }
    public LocalDate getData() { return data; }
    public LocalTime getOraInizio() { return oraInizio; }
    public LocalTime getOraFine() { return oraFine; }
    public StatoAppuntamento getStato() { return stato; }
    public String getNoteCliente() { return noteCliente; }
    public String getNoteInterne() { return noteInterne; }
    public boolean isRichiestoDalCliente() { return richiestoDalCliente; }
    public OffsetDateTime getCreatoIl() { return creatoIl; }
}
