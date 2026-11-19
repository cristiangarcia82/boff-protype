package it.boff.bike.utente;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registrazione autonoma di un cliente, o creazione di un collaboratore (password scelta). */
public class DatiRegistrazione extends DatiAnagrafica {

    public static final int PASSWORD_MIN = 8;

    @NotBlank(message = "Scegli una password")
    @Size(min = PASSWORD_MIN, max = 100, message = "Almeno " + PASSWORD_MIN + " caratteri")
    private String password;

    @AssertTrue(message = "Per registrarti devi accettare l'informativa privacy")
    private boolean privacyAccettata;

    /** Chi si registra da solo (o un collaboratore) deve avere l'email: è il nome utente. */
    @Override
    @NotBlank(message = "Indica l'email")
    public String getEmail() { return super.getEmail(); }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isPrivacyAccettata() { return privacyAccettata; }
    public void setPrivacyAccettata(boolean privacyAccettata) { this.privacyAccettata = privacyAccettata; }
}
