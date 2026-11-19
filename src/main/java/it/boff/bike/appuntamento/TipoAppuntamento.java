package it.boff.bike.appuntamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tipo di consulenza che i clienti possono prenotare (catalogo gestito dal titolare). */
@Entity
@Table(name = "tipo_appuntamento")
public class TipoAppuntamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Indica il nome")
    @Size(max = 80, message = "Massimo 80 caratteri")
    @Column(nullable = false)
    private String nome;

    @Size(max = 300, message = "Massimo 300 caratteri")
    private String descrizione;

    @NotNull(message = "Indica la durata")
    @Min(value = 10, message = "Almeno 10 minuti")
    @Max(value = 240, message = "Al massimo 4 ore")
    @Column(name = "durata_min", nullable = false)
    private Integer durataMin;

    @Column(nullable = false)
    private boolean attivo = true;

    @Column(nullable = false)
    private int ordine;

    public TipoAppuntamento() {
    }

    public TipoAppuntamento(String nome, String descrizione, int durataMin) {
        this.nome = nome;
        this.descrizione = descrizione;
        this.durataMin = durataMin;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescrizione() { return descrizione; }
    public void setDescrizione(String descrizione) { this.descrizione = descrizione; }
    public Integer getDurataMin() { return durataMin; }
    public void setDurataMin(Integer durataMin) { this.durataMin = durataMin; }
    public boolean isAttivo() { return attivo; }
    public void setAttivo(boolean attivo) { this.attivo = attivo; }
    public int getOrdine() { return ordine; }
    public void setOrdine(int ordine) { this.ordine = ordine; }
}
