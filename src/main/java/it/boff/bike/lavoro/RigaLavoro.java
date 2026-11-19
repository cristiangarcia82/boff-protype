package it.boff.bike.lavoro;

import it.boff.bike.catalogo.TipoIntervento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Un intervento da svolgere nell'ambito di un lavoro, con il suo tempo previsto. */
@Entity
@Table(name = "riga_lavoro")
public class RigaLavoro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lavoro_id", nullable = false, updatable = false)
    private Lavoro lavoro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_intervento_id")
    private TipoIntervento tipoIntervento;

    @Column(nullable = false)
    private String descrizione;

    @Column(name = "durata_min", nullable = false)
    private int durataMin;

    @Column(nullable = false)
    private boolean completata;

    @Column(nullable = false)
    private int ordine;

    /** Solo per gli interventi extra proposti al cliente; null = intervento normale. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Approvazione approvazione;

    @Column(name = "nota_approvazione")
    private String notaApprovazione;

    @Column(name = "risposta_il")
    private java.time.OffsetDateTime rispostaIl;

    /** Minuti realmente impiegati (funzionalità "tempo effettivo"). */
    @Column(name = "minuti_effettivi")
    private Integer minutiEffettivi;

    protected RigaLavoro() {
    }

    RigaLavoro(Lavoro lavoro, TipoIntervento tipo, String descrizione, int durataMin, int ordine) {
        this.lavoro = lavoro;
        this.tipoIntervento = tipo;
        this.descrizione = descrizione;
        this.durataMin = durataMin;
        this.ordine = ordine;
    }

    public Long getId() { return id; }
    public Lavoro getLavoro() { return lavoro; }
    public TipoIntervento getTipoIntervento() { return tipoIntervento; }
    public String getDescrizione() { return descrizione; }
    public int getDurataMin() { return durataMin; }
    public boolean isCompletata() { return completata; }
    public int getOrdine() { return ordine; }
    public Approvazione getApprovazione() { return approvazione; }
    public String getNotaApprovazione() { return notaApprovazione; }
    public java.time.OffsetDateTime getRispostaIl() { return rispostaIl; }
    public Integer getMinutiEffettivi() { return minutiEffettivi; }

    /** Da fare davvero: interventi normali o extra approvati (non quelli in attesa o rifiutati). */
    public boolean isValida() {
        return approvazione == null || approvazione == Approvazione.APPROVATA;
    }

    public boolean isInAttesaDiApprovazione() {
        return approvazione == Approvazione.DA_APPROVARE;
    }

    void proponi(String nota) {
        this.approvazione = Approvazione.DA_APPROVARE;
        this.notaApprovazione = nota;
    }

    void rispondi(boolean approvata, java.time.OffsetDateTime quando) {
        this.approvazione = approvata ? Approvazione.APPROVATA : Approvazione.RIFIUTATA;
        this.rispostaIl = quando;
    }

    void setMinutiEffettivi(Integer minuti) { this.minutiEffettivi = minuti; }

    void setDurataMin(int durataMin) { this.durataMin = durataMin; }
    void setCompletata(boolean completata) {
        this.completata = completata;
        if (!completata) this.minutiEffettivi = null;
    }
}
