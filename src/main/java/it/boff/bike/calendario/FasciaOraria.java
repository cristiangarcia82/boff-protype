package it.boff.bike.calendario;

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

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Fascia dell'orario settimanale di una persona (es. martedì 9:00–12:30, officina). */
@Entity
@Table(name = "fascia_oraria")
public class FasciaOraria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false, updatable = false)
    private Utente utente;

    @Column(name = "giorno_settimana", nullable = false)
    private int giornoSettimana;

    @Column(name = "ora_inizio", nullable = false)
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    private LocalTime oraFine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFascia tipo;

    protected FasciaOraria() {
    }

    FasciaOraria(Utente utente, DayOfWeek giorno, LocalTime inizio, LocalTime fine, TipoFascia tipo) {
        this.utente = utente;
        this.giornoSettimana = giorno.getValue();
        this.oraInizio = inizio;
        this.oraFine = fine;
        this.tipo = tipo;
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public DayOfWeek getGiorno() { return DayOfWeek.of(giornoSettimana); }
    public LocalTime getOraInizio() { return oraInizio; }
    public LocalTime getOraFine() { return oraFine; }
    public TipoFascia getTipo() { return tipo; }

    public Intervallo intervallo() {
        return new Intervallo(oraInizio, oraFine);
    }
}
