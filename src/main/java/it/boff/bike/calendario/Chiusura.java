package it.boff.bike.calendario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** Giorno di chiusura del negozio (oltre alle festività nazionali). */
@Entity
@Table(name = "chiusura")
public class Chiusura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate data;

    @Column(nullable = false)
    private String descrizione;

    protected Chiusura() {
    }

    Chiusura(LocalDate data, String descrizione) {
        this.data = data;
        this.descrizione = descrizione;
    }

    public Long getId() { return id; }
    public LocalDate getData() { return data; }
    public String getDescrizione() { return descrizione; }
}
