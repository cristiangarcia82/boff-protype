package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import it.boff.bike.accesso.PasskeyService;

/**
 * Riquadro "Accedi con Face ID / Touch ID": compare dopo l'accesso solo se il dispositivo lo supporta,
 * l'utente non ha ancora passkey e non ha scelto "Non ora" su questo dispositivo.
 */
public class ProponiPasskey extends Div {

    private static final String NASCOSTO = "boff-passkey-non-ora";

    private final PasskeyService passkey;
    private final String email;

    public ProponiPasskey(PasskeyService passkey, String email) {
        this.passkey = passkey;
        this.email = email;
        addClassNames("boff-card", "boff-attiva-notifiche");
        setVisible(false);

        Paragraph testo = new Paragraph("Entra più in fretta: la prossima volta basta Face ID, Touch ID o l'impronta, "
                + "senza password.");
        Button attiva = new Button("Attiva", VaadinIcon.KEY.create(), e -> attiva());
        attiva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button nonOra = new Button("Non ora", e -> {
            getElement().executeJs("try { localStorage.setItem($0, '1') } catch (e) {}", NASCOSTO);
            setVisible(false);
        });
        nonOra.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        add(testo, new Div(attiva, nonOra));
    }

    @Override
    protected void onAttach(AttachEvent event) {
        if (email == null || !passkey.elenco(email).isEmpty()) return;
        UI ui = event.getUI();
        ui.getPage().executeJs("try { return localStorage.getItem($0) === '1' } catch (e) { return false }", NASCOSTO)
                .then(Boolean.class, nascosto -> {
                    if (!nascosto) Passkey.supportata(ui, this::setVisible);
                });
    }

    private void attiva() {
        Passkey.registra(UI.getCurrent(), esito -> {
            switch (esito) {
                case "ok", "gia" -> {
                    setVisible(false);
                    Avvisi.ok("Fatto! La prossima volta scegli \"Accedi con Face ID / Touch ID\"");
                }
                case "annullato" -> { }
                default -> Avvisi.errore("Non è stato possibile attivarlo su questo dispositivo");
            }
        });
    }
}
