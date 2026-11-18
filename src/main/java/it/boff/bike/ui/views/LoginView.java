package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "login", autoLayout = false)
@PageTitle("Accedi | Boff Bike")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView() {
        addClassName("boff-login");
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        login.setAction("login");
        login.setForgotPasswordButtonVisible(false);
        login.setI18n(i18nItaliano());

        add(login, new Paragraph("Officina e negozio di biciclette"));
    }

    private static LoginI18n i18nItaliano() {
        LoginI18n.Form form = new LoginI18n.Form();
        form.setTitle("Boff Bike");
        form.setUsername("Email");
        form.setPassword("Password");
        form.setSubmit("Accedi");
        LoginI18n.ErrorMessage errore = new LoginI18n.ErrorMessage();
        errore.setTitle("Accesso non riuscito");
        errore.setMessage("Email o password non corretti. Riprova.");
        LoginI18n i18n = new LoginI18n();
        i18n.setForm(form);
        i18n.setErrorMessage(errore);
        return i18n;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        login.setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }
}
