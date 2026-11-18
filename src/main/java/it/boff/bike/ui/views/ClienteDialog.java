package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.utente.DatiAnagrafica;

import java.util.function.Consumer;

/** Nuovo cliente creato dal negozio, o modifica dei suoi dati. */
class ClienteDialog extends Dialog {

    ClienteDialog(DatiAnagrafica dati, boolean nuovo, Consumer<DatiAnagrafica> salva) {
        setHeaderTitle(nuovo ? "Nuovo cliente" : "Modifica cliente");
        setWidth("min(560px, 100vw)");

        TextField nome = new TextField("Nome");
        TextField cognome = new TextField("Cognome");
        EmailField email = new EmailField("Email");
        email.setReadOnly(!nuovo);
        if (nuovo) {
            email.setHelperText("Il cliente imposterà la password con \"Password dimenticata?\"");
        }
        TextField telefono = new TextField("Telefono");

        BeanValidationBinder<DatiAnagrafica> binder = new BeanValidationBinder<>(DatiAnagrafica.class);
        binder.forField(nome).bind("nome");
        binder.forField(cognome).bind("cognome");
        binder.forField(email).bind("email");
        binder.forField(telefono).bind("telefono");
        binder.readBean(dati);

        FormLayout form = new FormLayout(nome, cognome, email, telefono);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("440px", 2));
        add(form);

        Button ok = new Button("Salva", e -> {
            DatiAnagrafica risultato = new DatiAnagrafica();
            if (binder.writeBeanIfValid(risultato)) {
                try {
                    salva.accept(risultato);
                    close();
                } catch (ErroreDominio ex) {
                    Avvisi.errore(ex.getMessage());
                }
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Annulla", e -> close()), ok);
        nome.focus();
    }
}
