package it.boff.bike.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.utente.DatiRegistrazione;
import it.boff.bike.utente.UtenteService;

/** Registrazione autonoma del cliente. */
@Route(value = "registrati", autoLayout = false)
@PageTitle("Registrati | Boff Bike")
@AnonymousAllowed
public class RegistrazioneView extends Div {

    public RegistrazioneView(UtenteService utenti) {
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-login");

        TextField nome = new TextField("Nome");
        TextField cognome = new TextField("Cognome");
        EmailField email = new EmailField("Email");
        email.setHelperText("Sarà il tuo nome utente");
        TextField telefono = new TextField("Telefono");
        telefono.setHelperText("Ti chiameremo solo per la tua bici");
        PasswordField password = new PasswordField("Password");
        password.setHelperText("Almeno " + DatiRegistrazione.PASSWORD_MIN + " caratteri");
        PasswordField conferma = new PasswordField("Ripeti la password");
        Checkbox privacy = new Checkbox();
        privacy.setLabelComponent(new Span(new Span("Ho letto e accetto l'"),
                new Anchor("privacy", "informativa privacy", com.vaadin.flow.component.html.AnchorTarget.BLANK)));
        for (var campo : new com.vaadin.flow.component.HasValue[]{nome, cognome, email, telefono, password}) {
            ((com.vaadin.flow.component.HasValue<?, ?>) campo).setRequiredIndicatorVisible(true);
        }
        conferma.setRequiredIndicatorVisible(true);

        BeanValidationBinder<DatiRegistrazione> binder = new BeanValidationBinder<>(DatiRegistrazione.class);
        binder.forField(nome).bind("nome");
        binder.forField(cognome).bind("cognome");
        binder.forField(email).bind("email");
        binder.forField(telefono).bind("telefono");
        binder.forField(password).bind("password");
        binder.forField(privacy).bind("privacyAccettata");
        binder.forField(conferma)
                .withValidator(v -> v != null && v.equals(password.getValue()), "Le password non coincidono")
                .bind(d -> d.getPassword(), (d, v) -> { });

        FormLayout form = new FormLayout(nome, cognome, email, telefono, password, conferma, privacy);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("440px", 2));
        form.setColspan(email, 2);
        form.setColspan(telefono, 2);
        form.setColspan(privacy, 2);

        Paragraph errore = new Paragraph();
        errore.addClassName("boff-errore");
        errore.setVisible(false);

        Button registrati = new Button("Crea account", e -> {
            DatiRegistrazione dati = new DatiRegistrazione();
            if (!binder.writeBeanIfValid(dati)) {
                return;
            }
            try {
                utenti.registraCliente(dati);
                getUI().ifPresent(ui -> ui.navigate("login?registrato"));
            } catch (ErroreDominio ex) {
                errore.setText(ex.getMessage());
                errore.setVisible(true);
            }
        });
        registrati.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        registrati.addClickShortcut(Key.ENTER);
        registrati.setWidthFull();

        Div card = new Div(new H2("Crea il tuo account"),
                new Paragraph("Registra le tue bici e avvisaci dei lavori da fare prima di portarle in negozio."),
                form, errore, registrati,
                new Paragraph(new Span("Hai già un account? "), new RouterLink("Accedi", LoginView.class)));
        card.addClassNames("boff-card", "boff-card-modulo");
        add(card);
    }
}
