package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import it.boff.bike.accesso.CodiceAccessoService;
import it.boff.bike.ui.componenti.Passkey;

/**
 * Accesso senza password: si chiede un codice di 6 cifre via email e lo si scrive qui.
 * Il codice viene inviato al normale modulo di login (stessa protezione e "resta collegato").
 */
@Route(value = "accedi-con-codice", autoLayout = false)
@PageTitle("Accedi con un codice | Boff Bike")
@AnonymousAllowed
public class AccessoCodiceView extends VerticalLayout {

    private final CodiceAccessoService codici;
    private final Div contenuto = new Div();
    private java.time.Instant ultimoInvio = java.time.Instant.EPOCH;

    public AccessoCodiceView(CodiceAccessoService codici) {
        this.codici = codici;
        addClassName("boff-login");
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        contenuto.addClassNames("boff-card", "boff-codice");
        com.vaadin.flow.component.html.Image logo = new com.vaadin.flow.component.html.Image("icons/logo.svg", "Boff Bike");
        logo.addClassName("boff-logo-grande");
        add(logo, contenuto, new RouterLink("Torna all'accesso con password", LoginView.class));
        chiediEmail(null);
    }

    private void chiediEmail(String precedente) {
        contenuto.removeAll();
        EmailField email = new EmailField("Email");
        email.setWidthFull();
        email.setRequiredIndicatorVisible(true);
        email.setAutocomplete(com.vaadin.flow.component.textfield.Autocomplete.EMAIL);
        if (precedente != null) email.setValue(precedente);
        Button invia = new Button("Inviami il codice", e -> {
            String indirizzo = email.getValue() == null ? "" : email.getValue().trim();
            if (indirizzo.isEmpty() || email.isInvalid()) {
                email.setInvalid(true);
                email.setErrorMessage("Scrivi l'email con cui ti sei registrato");
                return;
            }
            codici.richiedi(indirizzo);
            ultimoInvio = java.time.Instant.now();
            chiediCodice(indirizzo);
        });
        invia.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        invia.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
        invia.setWidthFull();
        Paragraph aiuto = new Paragraph("Ti mandiamo un codice di 6 cifre: niente password da ricordare.");
        aiuto.addClassName("boff-testo-secondario");
        contenuto.add(new H2("Accedi con un codice"), aiuto, email, invia);
        email.focus();
    }

    private void chiediCodice(String indirizzo) {
        contenuto.removeAll();
        Paragraph spiegazione = new Paragraph("Se " + indirizzo + " è registrata, ti abbiamo appena inviato un codice "
                + "di 6 cifre. Controlla anche nella posta indesiderata.");

        // modulo HTML classico verso /login: così valgono le stesse regole del login con password
        // (blocco dopo 5 errori, cambio di sessione, "resta collegato")
        Element modulo = new Element("form");
        modulo.setAttribute("method", "post");
        modulo.setAttribute("action", "login");
        modulo.getClassList().add("boff-modulo-codice");
        modulo.appendChild(nascosto("username", indirizzo));
        String[] csrf = Passkey.csrfModulo();
        if (!csrf[0].isEmpty()) {
            modulo.appendChild(nascosto(csrf[0], csrf[1]));
        }
        Element etichetta = new Element("label");
        etichetta.setAttribute("for", "boff-codice");
        etichetta.setText("Codice");
        Element campo = new Element("input");
        campo.setAttribute("id", "boff-codice");
        campo.setAttribute("name", "password");
        campo.setAttribute("type", "text");
        campo.setAttribute("inputmode", "numeric");
        campo.setAttribute("autocomplete", "one-time-code");
        campo.setAttribute("pattern", "[0-9]{6}");
        campo.setAttribute("maxlength", "6");
        campo.setAttribute("required", true);
        campo.setAttribute("autofocus", true);
        campo.setAttribute("placeholder", "123456");
        Element entra = new Element("button");
        entra.setAttribute("type", "submit");
        entra.setText("Entra");
        modulo.appendChild(etichetta, campo, entra);

        Button rinvia = new Button("Non è arrivato? Invialo di nuovo", e -> {
            // lo stesso limite del servizio (30 secondi tra un invio e l'altro): niente conferme non vere
            long attesa = 30 - java.time.Duration.between(ultimoInvio, java.time.Instant.now()).toSeconds();
            if (attesa > 0) {
                it.boff.bike.ui.componenti.Avvisi.errore("Attendi ancora " + attesa + " secondi prima di chiederne un altro");
                return;
            }
            codici.richiedi(indirizzo);
            ultimoInvio = java.time.Instant.now();
            it.boff.bike.ui.componenti.Avvisi.ok("Se l'email è registrata, ti abbiamo inviato un nuovo codice");
        });
        rinvia.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Button cambia = new Button("Usa un'altra email", e -> chiediEmail(indirizzo));
        cambia.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Paragraph validita = new Paragraph("Il codice vale " + CodiceAccessoService.VALIDITA.toMinutes()
                + " minuti. Non comunicarlo a nessuno.");
        validita.addClassName("boff-testo-secondario");

        Div modDiv = new Div();
        modDiv.getElement().appendChild(modulo);
        contenuto.add(new H2("Scrivi il codice"), spiegazione, modDiv, validita, new Div(rinvia, cambia));
    }

    private static Element nascosto(String nome, String valore) {
        Element e = new Element("input");
        e.setAttribute("type", "hidden");
        e.setAttribute("name", nome);
        e.setAttribute("value", valore);
        return e;
    }
}
