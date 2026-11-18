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

import java.time.LocalDate;

/** Una o più giornate intere in cui una persona non lavora. */
@Entity
@Table(name = "assenza")
public class Assenza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utente_id", nullable = false, updatable = false)
    private Utente utente;

    @Column(nullable = false)
    private LocalDate dal;

    @Column(nullable = false)
    private LocalDate al;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MotivoAssenza motivo;

    private String note;

    protected Assenza() {
    }

    Assenza(Utente utente, LocalDate dal, LocalDate al, MotivoAssenza motivo, String note) {
        this.utente = utente;
        this.dal = dal;
        this.al = al;
        this.motivo = motivo;
        this.note = note;
    }

    public boolean comprende(LocalDate giorno) {
        return !giorno.isBefore(dal) && !giorno.isAfter(al);
    }

    public Long getId() { return id; }
    public Utente getUtente() { return utente; }
    public LocalDate getDal() { return dal; }
    public LocalDate getAl() { return al; }
    public MotivoAssenza getMotivo() { return motivo; }
    public String getNote() { return note; }
}
