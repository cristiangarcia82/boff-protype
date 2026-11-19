package it.boff.bike.calendario;

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

/**
 * Tempo al banco inserito a mano dal titolare per un giorno preciso: in quell'intervallo la persona
 * non fa lavori di officina né appuntamenti.
 */
@Entity
@Table(name = "turno_vendita")
public class TurnoVendita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false, updatable = false)
    private Utente utente;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "ora_inizio", nullable = false)
    private LocalTime oraInizio;

    @Column(name = "ora_fine", nullable = false)
    private LocalTime oraFine;

    private String note;

    protected TurnoVendita() {
    }

    TurnoVendita(Utente utente, LocalDate data, Intervallo orario, String note) {
        this.utente = utente;
        this.data = data;
        this.oraInizio = orario.inizio();
        this.oraFine = orario.fine();
        this.note = note;
    }

    public Intervallo intervallo() {
        return new Intervallo(oraInizio, oraFine);
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public LocalDate getData() { return data; }
    public LocalTime getOraInizio() { return oraInizio; }
    public LocalTime getOraFine() { return oraFine; }
    public String getNote() { return note; }
}
