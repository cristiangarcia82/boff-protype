package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;
import java.util.Map;

@Route(value = "login", autoLayout = false)
@PageTitle("Accedi | Boff Bike")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();
    private final Paragraph messaggio = new Paragraph();

    public LoginView() {
        addClassName("boff-login");
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        login.setAction("login");
        login.setForgotPasswordButtonVisible(true);
        login.addForgotPasswordListener(e -> getUI().ifPresent(ui -> ui.navigate("password-dimenticata")));
        login.setI18n(i18nItaliano());

        messaggio.addClassName("boff-info");
        messaggio.setVisible(false);

        // modi di accesso senza password
        com.vaadin.flow.component.button.Button passkey = new com.vaadin.flow.component.button.Button(
                "Accedi con Face ID / Touch ID", com.vaadin.flow.component.icon.VaadinIcon.KEY.create(), e ->
                it.boff.bike.ui.componenti.Passkey.accedi(com.vaadin.flow.component.UI.getCurrent(), esito -> {
                    if ("errore".equals(esito)) {
                        it.boff.bike.ui.componenti.Avvisi.errore("Accesso non riuscito. Su questo dispositivo è attivo? "
                                + "Puoi attivarlo dal profilo dopo essere entrato con password o codice.");
                    }
                }));
        passkey.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
        passkey.setVisible(false);
        passkey.setWidth("min(320px, 100%)");
        it.boff.bike.ui.componenti.Passkey.supportata(com.vaadin.flow.component.UI.getCurrent(), passkey::setVisible);
        com.vaadin.flow.component.button.Button codice = new com.vaadin.flow.component.button.Button(
                "Ricevi un codice via email", com.vaadin.flow.component.icon.VaadinIcon.ENVELOPE_O.create(),
                e -> getUI().ifPresent(ui -> ui.navigate(AccessoCodiceView.class)));
        codice.setWidth("min(320px, 100%)");
        Paragraph senzaPassword = new Paragraph("Oppure, senza password:");
        senzaPassword.addClassName("boff-testo-secondario");

        add(messaggio, login, senzaPassword, passkey, codice,
                new Paragraph(new Span("Nuovo cliente? "), new RouterLink("Crea un account", RegistrazioneView.class)));
    }

    private static LoginI18n i18nItaliano() {
        LoginI18n.Form form = new LoginI18n.Form();
        form.setTitle("Boff Bike");
        form.setUsername("Email");
        form.setPassword("Password");
        form.setSubmit("Accedi");
        form.setForgotPassword("Password dimenticata?");
        LoginI18n.ErrorMessage errore = new LoginI18n.ErrorMessage();
        errore.setTitle("Accesso non riuscito");
        errore.setMessage("Email, password o codice non corretti (il codice vale 10 minuti). "
                + "Dopo 5 tentativi l'accesso è sospeso per 15 minuti.");
        LoginI18n i18n = new LoginI18n();
        i18n.setForm(form);
        i18n.setErrorMessage(errore);
        return i18n;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> parametri = event.getLocation().getQueryParameters().getParameters();
        login.setError(parametri.containsKey("error"));
        String testo = null;
        if (parametri.containsKey("registrato")) {
            testo = "Account creato! Ora puoi accedere.";
        } else if (parametri.containsKey("password-impostata")) {
            testo = "Password impostata. Ora puoi accedere.";
        } else if (parametri.containsKey("email-inviata")) {
            testo = "Se l'email è registrata riceverai a breve un link per impostare la password.";
        }
        messaggio.setText(testo == null ? "" : testo);
        messaggio.setVisible(testo != null);
    }
}
