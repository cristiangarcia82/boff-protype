package it.boff.bike.utente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dati anagrafici e di contatto (registrazione, creazione dal negozio, profilo). */
public class DatiAnagrafica {

    @NotBlank(message = "Indica il nome")
    @Size(max = 100, message = "Massimo 100 caratteri")
    private String nome;

    @NotBlank(message = "Indica il cognome")
    @Size(max = 100, message = "Massimo 100 caratteri")
    private String cognome;

    /** Facoltativa per i clienti creati dal negozio (senza email il cliente non usa l'app). */
    @Email(message = "Email non valida")
    @Size(max = 255, message = "Email troppo lunga")
    private String email;

    @NotBlank(message = "Indica un numero di telefono")
    @Pattern(regexp = "^\\+?[0-9 ()./-]{6,20}$", message = "Numero di telefono non valido")
    private String telefono;

    public static DatiAnagrafica da(Utente u) {
        DatiAnagrafica d = new DatiAnagrafica();
        d.nome = u.getNome();
        d.cognome = u.getCognome();
        d.email = u.getEmail();
        d.telefono = u.getTelefono();
        return d;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCognome() { return cognome; }
    public void setCognome(String cognome) { this.cognome = cognome; }
    public String getEmail() { return email; }
    /** Vuota o solo spazi = nessuna email. */
    public void setEmail(String email) { this.email = email == null || email.isBlank() ? null : email.trim(); }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
