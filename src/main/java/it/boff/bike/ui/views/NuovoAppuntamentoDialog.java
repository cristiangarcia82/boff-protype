package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import it.boff.bike.appuntamento.AppuntamentoService;
import it.boff.bike.appuntamento.TipoAppuntamento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.CampoData;
import it.boff.bike.utente.DatiAnagrafica;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.TreeSet;

/** Il negozio fissa un appuntamento (al banco o al telefono): è già confermato. */
class NuovoAppuntamentoDialog extends Dialog {

    NuovoAppuntamentoDialog(AppuntamentoService service, UtenteService utenti, List<Utente> personale, LocalDate oggi,
                            Runnable creato) {
        setHeaderTitle("Nuovo appuntamento");
        setWidth("min(620px, 100vw)");

        ComboBox<Utente> cliente = new ComboBox<>("Cliente");
        cliente.setItems(q -> utenti.cercaClienti(q.getFilter().orElse("")).stream().skip(q.getOffset()).limit(q.getLimit()));
        cliente.setItemLabelGenerator(u -> u.getNomeCompleto() + (u.getTelefono() == null ? "" : " · " + u.getTelefono()));
        cliente.setHelperText("Cerca per nome o telefono");
        cliente.setWidthFull();
        Button nuovoCliente = new Button("Nuovo cliente", VaadinIcon.PLUS.create(), e ->
                new ClienteDialog(new DatiAnagrafica(), true, dati -> {
                    cliente.setValue(utenti.creaCliente(dati, true));
                    Avvisi.ok("Cliente creato");
                }).open());
        nuovoCliente.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Div rigaCliente = new Div(cliente, nuovoCliente);
        rigaCliente.addClassName("boff-riga-campo");

        Select<TipoAppuntamento> tipo = new Select<>();
        tipo.setLabel("Tipo");
        tipo.setItems(service.tipiAttivi());
        tipo.setItemLabelGenerator(t -> t.getNome() + " (" + t.getDurataMin() + " min)");
        Select<Utente> persona = new Select<>();
        persona.setLabel("Con");
        persona.setItems(personale);
        persona.setItemLabelGenerator(u -> u == null ? "Chi è libero" : u.getNomeCompleto());
        persona.setEmptySelectionAllowed(true);
        persona.setEmptySelectionCaption("Chi è libero");
        DatePicker giorno = CampoData.crea("Giorno");
        giorno.setMin(oggi);
        giorno.setValue(oggi);
        Select<LocalTime> ora = new Select<>();
        ora.setLabel("Ora");
        Runnable aggiorna = () -> {
            if (tipo.getValue() == null || giorno.getValue() == null) return;
            TreeSet<LocalTime> liberi = new TreeSet<>();
            for (Utente u : persona.getValue() == null ? personale : List.of(persona.getValue())) {
                liberi.addAll(service.orariLiberiDi(u.getId(), giorno.getValue(), tipo.getValue().getDurataMin(), null));
            }
            ora.setItems(liberi);
            ora.setPlaceholder(liberi.isEmpty() ? "Nessun orario libero nelle ore di vendita" : "Scegli…");
        };
        tipo.addValueChangeListener(e -> aggiorna.run());
        persona.addValueChangeListener(e -> aggiorna.run());
        giorno.addValueChangeListener(e -> aggiorna.run());
        TextArea note = new TextArea("Note interne");
        note.setMaxLength(AppuntamentoService.MAX_NOTE);

        FormLayout form = new FormLayout(tipo, persona, giorno, ora, note);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("440px", 2));
        form.setColspan(note, 2);
        add(rigaCliente, form);

        Button salva = new Button("Fissa appuntamento", e -> {
            if (cliente.getValue() == null || tipo.getValue() == null || giorno.getValue() == null || ora.getValue() == null) {
                Avvisi.errore("Scegli cliente, tipo, giorno e ora");
                return;
            }
            try {
                service.crea(cliente.getValue().getId(), tipo.getValue().getId(),
                        persona.getValue() == null ? null : persona.getValue().getId(), giorno.getValue(), ora.getValue(),
                        note.getValue());
                Avvisi.ok("Appuntamento fissato" + (cliente.getValue().usaApp() ? ": il cliente riceve un avviso" : ""));
                close();
                creato.run();
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        salva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Annulla", e -> close()), salva);
    }
}
