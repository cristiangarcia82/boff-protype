package it.boff.bike.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.notifiche.RecuperoPasswordService;
import it.boff.bike.ui.componenti.Avvisi;
import jakarta.validation.ConstraintViolationException;

/** Pagina aperta dal link ricevuto via email. */
@Route(value = "reimposta-password", autoLayout = false)
@PageTitle("Nuova password | Boff Bike")
@AnonymousAllowed
public class ReimpostaPasswordView extends Div implements BeforeEnterObserver {

    private final RecuperoPasswordService recupero;

    public ReimpostaPasswordView(RecuperoPasswordService recupero) {
        this.recupero = recupero;
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-login");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String token = event.getLocation().getQueryParameters().getSingleParameter("token").orElse(null);
        Div card = new Div();
        card.addClassNames("boff-card", "boff-card-modulo", "boff-colonna");
        add(card);
        if (!recupero.tokenValido(token)) {
            card.add(new H2("Link non valido"),
                    new Paragraph("Il link è scaduto o è già stato usato."),
                    new RouterLink("Richiedine uno nuovo", PasswordDimenticataView.class));
            return;
        }
        PasswordField nuova = new PasswordField("Nuova password");
        nuova.setHelperText("Almeno 8 caratteri");
        nuova.setWidthFull();
        PasswordField conferma = new PasswordField("Ripeti la password");
        conferma.setWidthFull();
        Button salva = new Button("Salva password", e -> {
            if (!nuova.getValue().equals(conferma.getValue())) {
                conferma.setInvalid(true);
                conferma.setErrorMessage("Le password non coincidono");
                return;
            }
            try {
                recupero.reimposta(token, nuova.getValue());
                getUI().ifPresent(ui -> ui.navigate("login?password-impostata"));
            } catch (ConstraintViolationException ex) {
                nuova.setInvalid(true);
                nuova.setErrorMessage("Almeno 8 caratteri");
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        salva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        salva.addClickShortcut(Key.ENTER);
        salva.setWidthFull();
        card.add(new H2("Scegli la password"), nuova, conferma, salva);
    }
}
