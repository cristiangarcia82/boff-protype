package it.boff.bike.bicicletta;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dati del modulo "bicicletta", usati sia dall'interfaccia sia dal servizio. */
public class DatiBicicletta {

    @NotBlank(message = "Indica la marca")
    @Size(max = 60, message = "Massimo 60 caratteri")
    private String marca;

    @NotBlank(message = "Indica il modello")
    @Size(max = 80, message = "Massimo 80 caratteri")
    private String modello;

    @NotNull(message = "Scegli la tipologia")
    private Tipologia tipologia;

    private boolean elettrica;

    @Size(max = 20, message = "Massimo 20 caratteri")
    private String taglia;

    @Size(max = 40, message = "Massimo 40 caratteri")
    private String colore;

    @Min(value = 1950, message = "Anno non valido")
    @Max(value = 2100, message = "Anno non valido")
    private Integer anno;

    @Size(max = 40, message = "Massimo 40 caratteri")
    private String numeroTelaio;

    @Size(max = 500, message = "Massimo 500 caratteri")
    private String note;

    public DatiBicicletta() {
    }

    public static DatiBicicletta da(Bicicletta b) {
        DatiBicicletta d = new DatiBicicletta();
        d.marca = b.getMarca();
        d.modello = b.getModello();
        d.tipologia = b.getTipologia();
        d.elettrica = b.isElettrica();
        d.taglia = b.getTaglia();
        d.colore = b.getColore();
        d.anno = b.getAnno();
        d.numeroTelaio = b.getNumeroTelaio();
        d.note = b.getNote();
        return d;
    }

    public static DatiBicicletta di(String marca, String modello, Tipologia tipologia) {
        DatiBicicletta d = new DatiBicicletta();
        d.marca = marca;
        d.modello = modello;
        d.tipologia = tipologia;
        return d;
    }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }
    public String getModello() { return modello; }
    public void setModello(String modello) { this.modello = modello; }
    public Tipologia getTipologia() { return tipologia; }
    public void setTipologia(Tipologia tipologia) { this.tipologia = tipologia; }
    public boolean isElettrica() { return elettrica; }
    public void setElettrica(boolean elettrica) { this.elettrica = elettrica; }
    public String getTaglia() { return taglia; }
    public void setTaglia(String taglia) { this.taglia = taglia; }
    public String getColore() { return colore; }
    public void setColore(String colore) { this.colore = colore; }
    public Integer getAnno() { return anno; }
    public void setAnno(Integer anno) { this.anno = anno; }
    public String getNumeroTelaio() { return numeroTelaio; }
    public void setNumeroTelaio(String numeroTelaio) { this.numeroTelaio = numeroTelaio; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
