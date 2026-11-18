package it.boff.bike.lavoro;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Foto allegata a un lavoro (es. il problema fotografato dal cliente). */
@Entity
@Table(name = "foto_lavoro")
public class FotoLavoro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lavoro_id", nullable = false, updatable = false)
    private Lavoro lavoro;

    @Column(name = "nome_file", nullable = false)
    private String nomeFile;

    @Column(nullable = false)
    private String tipo;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] dati;

    @Column(nullable = false)
    private int dimensione;

    @Column(name = "dal_cliente", nullable = false)
    private boolean dalCliente;

    @Column(name = "caricata_il", nullable = false)
    private OffsetDateTime caricataIl;

    protected FotoLavoro() {
    }

    FotoLavoro(Lavoro lavoro, String nomeFile, String tipo, byte[] dati, boolean dalCliente, OffsetDateTime quando) {
        this.lavoro = lavoro;
        this.nomeFile = nomeFile;
        this.tipo = tipo;
        this.dati = dati;
        this.dimensione = dati.length;
        this.dalCliente = dalCliente;
        this.caricataIl = quando;
    }

    public Long getId() { return id; }
    public Lavoro getLavoro() { return lavoro; }
    public String getNomeFile() { return nomeFile; }
    public String getTipo() { return tipo; }
    public byte[] getDati() { return dati; }
    public int getDimensione() { return dimensione; }
    public boolean isDalCliente() { return dalCliente; }
    public OffsetDateTime getCaricataIl() { return caricataIl; }
}
