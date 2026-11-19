package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.privacy.PrivacyService;
import it.boff.bike.utente.Ruolo;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.utente.DatiAnagrafica;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.ConstraintViolationException;

/** Dati personali e cambio password, per ogni utente. */
@Route("profilo")
@PageTitle("Il mio profilo | Boff Bike")
@PermitAll
public class ProfiloView extends Div {

    public ProfiloView(UtenteService service, UtenteCorrente utenteCorrente, PrivacyService privacy,
                       it.boff.bike.funzionalita.FunzionalitaService funzionalita) {
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-colonna");
        Utente utente = utenteCorrente.get().orElseThrow();
        Long id = utente.getId();

        TextField nome = new TextField("Nome");
        TextField cognome = new TextField("Cognome");
        EmailField email = new EmailField("Email");
        email.setReadOnly(true);
        email.setHelperText("È il tuo nome utente: per cambiarla chiedi al negozio");
        TextField telefono = new TextField("Telefono");

        BeanValidationBinder<DatiAnagrafica> binder = new BeanValidationBinder<>(DatiAnagrafica.class);
        binder.forField(nome).bind("nome");
        binder.forField(cognome).bind("cognome");
        binder.forField(email).bind("email");
        binder.forField(telefono).bind("telefono");
        binder.readBean(DatiAnagrafica.da(utente));

        Button salva = new Button("Salva dati", e -> {
            DatiAnagrafica dati = DatiAnagrafica.da(service.trova(id));
            if (binder.writeBeanIfValid(dati)) {
                service.aggiornaAnagrafica(id, dati);
                Avvisi.ok("Dati salvati");
            }
        });
        salva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout dati = new FormLayout(nome, cognome, email, telefono);
        dati.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("440px", 2));
        Div schedaDati = new Div(new H3("I tuoi dati"), dati, salva);
        if (utente.getRuolo() == Ruolo.CLIENTE
                && funzionalita.attiva(it.boff.bike.funzionalita.Funzionalita.PROMEMORIA_MANUTENZIONE)) {
            com.vaadin.flow.component.checkbox.Checkbox promemoria = new com.vaadin.flow.component.checkbox.Checkbox(
                    "Ricevi i promemoria per il tagliando", utente.isPromemoriaAttivi());
            promemoria.addValueChangeListener(e -> {
                service.impostaPromemoria(id, e.getValue());
                Avvisi.ok(e.getValue() ? "Promemoria attivati" : "Promemoria disattivati");
            });
            schedaDati.add(new Div(promemoria));
        }
        schedaDati.addClassName("boff-card");

        PasswordField attuale = new PasswordField("Password attuale");
        PasswordField nuova = new PasswordField("Nuova password");
        nuova.setHelperText("Almeno 8 caratteri");
        PasswordField conferma = new PasswordField("Ripeti la nuova password");
        Button cambia = new Button("Cambia password", e -> {
            if (!nuova.getValue().equals(conferma.getValue())) {
                conferma.setInvalid(true);
                conferma.setErrorMessage("Le password non coincidono");
                return;
            }
            try {
                service.cambiaPassword(id, attuale.getValue(), nuova.getValue());
                attuale.clear();
                nuova.clear();
                conferma.clear();
                conferma.setInvalid(false);
                Avvisi.ok("Password cambiata");
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            } catch (ConstraintViolationException ex) {
                Avvisi.errore("La nuova password deve avere almeno 8 caratteri");
            }
        });
        FormLayout pw = new FormLayout(attuale, nuova, conferma);
        pw.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        Div schedaPw = new Div(new H3("Password"), pw, cambia);
        schedaPw.addClassName("boff-card");

        add(new H2("Il mio profilo"), schedaDati, schedaPw);
        if (utente.getRuolo() == Ruolo.CLIENTE) {
            add(schedaPrivacy(privacy, utenteCorrente, id));
        }
    }

    private Div schedaPrivacy(PrivacyService privacy, UtenteCorrente utenteCorrente, Long id) {
        Anchor scarica = new Anchor(DownloadHandler.fromInputStream(evento -> {
            byte[] dati = privacy.esporta(id).getBytes(StandardCharsets.UTF_8);
            return new DownloadResponse(new ByteArrayInputStream(dati), "i-miei-dati-boff-bike.json",
                    "application/json", dati.length);
        }), "Scarica i miei dati");
        scarica.getElement().setAttribute("download", true);

        Button elimina = new Button("Elimina il mio account", e -> {
            ConfirmDialog c = new ConfirmDialog("Eliminare l'account?",
                    "I tuoi dati personali verranno cancellati e non potrai più accedere. "
                            + "Lo storico dei lavori resta al negozio in forma anonima. L'operazione non si può annullare.",
                    "Elimina definitivamente", ev -> {
                        try {
                            privacy.eliminaAccount(id);
                            utenteCorrente.logout();
                        } catch (ErroreDominio ex) {
                            Avvisi.errore(ex.getMessage());
                        }
                    });
            c.setCancelable(true);
            c.setCancelText("Annulla");
            c.setConfirmButtonTheme("error primary");
            c.open();
        });
        elimina.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        Paragraph spiegazione = new Paragraph("Puoi scaricare una copia di tutti i dati che conserviamo su di te, "
                + "o eliminare il tuo account.");
        spiegazione.addClassName("boff-testo-secondario");
        Div d = new Div(new H3("I tuoi dati"), spiegazione, new Div(scarica), elimina);
        d.addClassNames("boff-card", "boff-colonna");
        d.getStyle().set("gap", "var(--lumo-space-s)");
        return d;
    }
}
