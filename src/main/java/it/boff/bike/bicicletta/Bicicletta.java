package it.boff.bike.bicicletta;

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

@Entity
@Table(name = "bicicletta")
public class Bicicletta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proprietario_id", nullable = false, updatable = false)
    private Utente proprietario;

    @Column(nullable = false)
    private String marca;

    @Column(nullable = false)
    private String modello;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipologia tipologia;

    @Column(nullable = false)
    private boolean elettrica;

    private String taglia;
    private String colore;
    private Integer anno;

    @Column(name = "numero_telaio")
    private String numeroTelaio;

    private String note;

    @Column(nullable = false)
    private boolean archiviata;

    @Column(name = "promemoria_inviato_il")
    private java.time.OffsetDateTime promemoriaInviatoIl;

    protected Bicicletta() {
    }

    Bicicletta(Utente proprietario) {
        this.proprietario = proprietario;
    }

    /** Copia i dati inseriti nel modulo (già validati). */
    void aggiorna(DatiBicicletta dati) {
        this.marca = dati.getMarca().trim();
        this.modello = dati.getModello().trim();
        this.tipologia = dati.getTipologia();
        this.elettrica = dati.isElettrica() || dati.getTipologia() == Tipologia.EBIKE;
        this.taglia = vuotoANull(dati.getTaglia());
        this.colore = vuotoANull(dati.getColore());
        this.anno = dati.getAnno();
        this.numeroTelaio = vuotoANull(dati.getNumeroTelaio());
        this.note = vuotoANull(dati.getNote());
    }

    void archivia() {
        this.archiviata = true;
    }

    private static String vuotoANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public Long getId() { return id; }
    public Utente getProprietario() { return proprietario; }
    public String getMarca() { return marca; }
    public String getModello() { return modello; }
    public Tipologia getTipologia() { return tipologia; }
    public boolean isElettrica() { return elettrica; }
    public String getTaglia() { return taglia; }
    public String getColore() { return colore; }
    public Integer getAnno() { return anno; }
    public String getNumeroTelaio() { return numeroTelaio; }
    public String getNote() { return note; }
    public boolean isArchiviata() { return archiviata; }
    public java.time.OffsetDateTime getPromemoriaInviatoIl() { return promemoriaInviatoIl; }
    public void segnaPromemoriaInviato(java.time.OffsetDateTime quando) { this.promemoriaInviatoIl = quando; }

    public String getNome() {
        return marca + " " + modello;
    }
}
