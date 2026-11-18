package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.utente.DatiRegistrazione;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

/** Account del personale (titolare e collaboratori). Solo per il titolare. */
@Route("utenti")
@PageTitle("Personale | Boff Bike")
@RolesAllowed(Ruoli.TITOLARE)
public class UtentiView extends Div {

    private final UtenteService service;
    private final Grid<Utente> griglia = new Grid<>();

    public UtentiView(UtenteService service) {
        this.service = service;
        addClassName("boff-pagina");

        Button nuovo = new Button("Nuovo collaboratore", VaadinIcon.PLUS.create(), e -> nuovoCollaboratore());
        nuovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div intestazione = new Div(new H2("Personale"), nuovo);
        intestazione.addClassName("boff-intestazione");

        griglia.addColumn(Utente::getNomeCompleto).setHeader("Nome").setAutoWidth(true).setFlexGrow(1);
        griglia.addColumn(Utente::getEmail).setHeader("Email").setAutoWidth(true);
        griglia.addColumn(u -> u.isTitolare() ? "Titolare" : "Collaboratore").setHeader("Ruolo").setAutoWidth(true);
        griglia.addComponentColumn(this::stato).setHeader("Stato").setAutoWidth(true);
        griglia.addComponentColumn(this::azioni).setAutoWidth(true);
        griglia.setAllRowsVisible(true);

        add(intestazione,
                new Paragraph("Ogni persona dello staff è un'unità lavorativa: i suoi orari si impostano in \"Orari\"."),
                griglia);
        aggiorna();
    }

    private void aggiorna() {
        griglia.setItems(service.personale());
    }

    private Span stato(Utente u) {
        Span s = new Span(u.isAttivo() ? "Attivo" : "Disattivato");
        s.getElement().getThemeList().add(u.isAttivo() ? "badge success" : "badge contrast");
        return s;
    }

    private Button azioni(Utente u) {
        Button b = new Button(u.isAttivo() ? "Disattiva" : "Riattiva", e -> {
            try {
                service.impostaAttivo(u.getId(), !u.isAttivo());
                aggiorna();
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        b.setVisible(!u.isTitolare());
        return b;
    }

    private void nuovoCollaboratore() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Nuovo collaboratore");
        d.setWidth("min(560px, 100vw)");
        TextField nome = new TextField("Nome");
        TextField cognome = new TextField("Cognome");
        EmailField email = new EmailField("Email");
        TextField telefono = new TextField("Telefono");
        PasswordField password = new PasswordField("Password iniziale");
        password.setHelperText("Comunicala al collaboratore: potrà cambiarla dal suo profilo");

        BeanValidationBinder<DatiRegistrazione> binder = new BeanValidationBinder<>(DatiRegistrazione.class);
        binder.forField(nome).bind("nome");
        binder.forField(cognome).bind("cognome");
        binder.forField(email).bind("email");
        binder.forField(telefono).bind("telefono");
        binder.forField(password).bind("password");

        FormLayout form = new FormLayout(nome, cognome, email, telefono, password);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("440px", 2));
        d.add(form);
        Button salva = new Button("Crea", e -> {
            DatiRegistrazione dati = new DatiRegistrazione();
            dati.setPrivacyAccettata(true); // non richiesta per il personale
            if (binder.writeBeanIfValid(dati)) {
                try {
                    service.creaCollaboratore(dati);
                    Avvisi.ok("Collaboratore creato");
                    aggiorna();
                    d.close();
                } catch (ErroreDominio ex) {
                    Avvisi.errore(ex.getMessage());
                }
            }
        });
        salva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(new Button("Annulla", e -> d.close()), salva);
        d.open();
    }
}
