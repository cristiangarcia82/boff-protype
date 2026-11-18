package it.boff.bike.notifiche;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Coppia chiave/valore salvata dall'app (non modificabile dall'interfaccia). */
@Entity
@Table(name = "impostazione")
public class Impostazione {

    @Id
    private String chiave;

    @Column(nullable = false)
    private String valore;

    protected Impostazione() {
    }

    public Impostazione(String chiave, String valore) {
        this.chiave = chiave;
        this.valore = valore;
    }

    public String getChiave() { return chiave; }
    public String getValore() { return valore; }
}
