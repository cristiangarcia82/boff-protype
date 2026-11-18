package it.boff.bike.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import it.boff.bike.notifiche.RecuperoPasswordService;

@Route(value = "password-dimenticata", autoLayout = false)
@PageTitle("Password dimenticata | Boff Bike")
@AnonymousAllowed
public class PasswordDimenticataView extends Div {

    public PasswordDimenticataView(RecuperoPasswordService recupero) {
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-login");
        EmailField email = new EmailField("Email");
        email.setWidthFull();
        email.setRequiredIndicatorVisible(true);
        Button invia = new Button("Inviami il link", e -> {
            if (email.isEmpty() || email.isInvalid()) {
                email.setInvalid(true);
                email.setErrorMessage("Inserisci l'email con cui ti sei registrato");
                return;
            }
            recupero.richiedi(email.getValue());
            getUI().ifPresent(ui -> ui.navigate("login?email-inviata"));
        });
        invia.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        invia.addClickShortcut(Key.ENTER);
        invia.setWidthFull();
        Div card = new Div(new H2("Password dimenticata"),
                new Paragraph("Ti invieremo un link per scegliere una nuova password. "
                        + "Vale anche se l'account te l'ha creato il negozio."),
                email, invia, new Paragraph(new RouterLink("Torna all'accesso", LoginView.class)));
        card.addClassNames("boff-card", "boff-card-modulo", "boff-colonna");
        add(card);
    }
}
