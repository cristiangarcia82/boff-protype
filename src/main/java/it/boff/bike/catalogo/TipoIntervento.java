package it.boff.bike.catalogo;

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

/** Voce del catalogo interventi con il suo tempo di lavoro standard. */
@Entity
@Table(name = "tipo_intervento")
public class TipoIntervento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Indica il nome")
    @Size(max = 80, message = "Massimo 80 caratteri")
    @Column(nullable = false)
    private String nome;

    @NotNull(message = "Indica la durata")
    @Min(value = 5, message = "Almeno 5 minuti")
    @Max(value = 1440, message = "Al massimo 24 ore")
    @Column(name = "durata_standard_min", nullable = false)
    private Integer durataStandardMin;

    @Column(nullable = false)
    private boolean attivo = true;

    @Column(nullable = false)
    private int ordine;

    public TipoIntervento() {
    }

    public TipoIntervento(String nome, int durataStandardMin) {
        this.nome = nome;
        this.durataStandardMin = durataStandardMin;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getDurataStandardMin() { return durataStandardMin; }
    public void setDurataStandardMin(Integer durataStandardMin) { this.durataStandardMin = durataStandardMin; }
    public boolean isAttivo() { return attivo; }
    public void setAttivo(boolean attivo) { this.attivo = attivo; }
    public int getOrdine() { return ordine; }
    public void setOrdine(int ordine) { this.ordine = ordine; }
}
