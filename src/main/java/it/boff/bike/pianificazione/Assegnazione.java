package it.boff.bike.pianificazione;

import it.boff.bike.calendario.Intervallo;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.utente.Utente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;

/** Un intervento (o un suo pezzo) messo in calendario per una persona, in un giorno e orario. */
@Entity
@Table(name = "assegnazione")
public class Assegnazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "riga_lavoro_id", nullable = false, updatable = false)
    private RigaLavoro riga;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false)
    private Utente utente;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "ora_inizio", nullable = false)
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    private LocalTime oraFine;

    @Column(nullable = false)
    private boolean bloccata;

    protected Assegnazione() {
    }

    Assegnazione(RigaLavoro riga, Utente utente, LocalDate data, Intervallo intervallo) {
        this.riga = riga;
        sposta(utente, data, intervallo);
    }

    void sposta(Utente utente, LocalDate data, Intervallo intervallo) {
        this.utente = utente;
        this.data = data;
        this.oraInizio = intervallo.inizio();
        this.oraFine = intervallo.fine();
    }

    void setBloccata(boolean bloccata) {
        this.bloccata = bloccata;
    }

    public Intervallo intervallo() {
        return new Intervallo(oraInizio, oraFine);
    }

    public Long getId() { return id; }
    public RigaLavoro getRiga() { return riga; }
    public Utente getUtente() { return utente; }
    public LocalDate getData() { return data; }
    public LocalTime getOraInizio() { return oraInizio; }
    public LocalTime getOraFine() { return oraFine; }
    public boolean isBloccata() { return bloccata; }
}
