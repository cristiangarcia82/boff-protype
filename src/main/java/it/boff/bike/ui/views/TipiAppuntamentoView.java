package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.appuntamento.AppuntamentoService;
import it.boff.bike.appuntamento.TipoAppuntamento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Avvisi;
import jakarta.annotation.security.RolesAllowed;

/** Il titolare decide quali consulenze si possono prenotare e quanto durano. */
@Route("tipi-appuntamento")
@PageTitle("Tipi di appuntamento | Boff Bike")
@RolesAllowed(Ruoli.TITOLARE)
public class TipiAppuntamentoView extends Div {

    private final AppuntamentoService service;
    private final Grid<TipoAppuntamento> griglia = new Grid<>();

    public TipiAppuntamentoView(AppuntamentoService service) {
        this.service = service;
        addClassNames("boff-pagina", "boff-colonna");
        Button nuovo = new Button("Nuovo tipo", VaadinIcon.PLUS.create(), e -> modifica(new TipoAppuntamento()));
        nuovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div intestazione = new Div(new H2("Tipi di appuntamento"), nuovo);
        intestazione.addClassName("boff-intestazione");
        Paragraph spiegazione = new Paragraph("Le consulenze che i clienti possono prenotare dall'app. La durata decide "
                + "quanto tempo occupa nell'orario di lavoro; un tipo disattivato non si può più prenotare.");
        spiegazione.addClassName("boff-testo-secondario");

        griglia.addColumn(TipoAppuntamento::getNome).setHeader("Nome").setFlexGrow(2);
        griglia.addColumn(t -> t.getDurataMin() + " min").setHeader("Durata").setAutoWidth(true);
        griglia.addColumn(t -> t.isAttivo() ? "Prenotabile" : "Disattivato").setHeader("Stato").setAutoWidth(true);
        griglia.addItemClickListener(e -> modifica(e.getItem()));
        griglia.setAllRowsVisible(true);
        add(intestazione, spiegazione, griglia);
        aggiorna();
    }

    private void aggiorna() {
        griglia.setItems(service.tuttiITipi());
    }

    private void modifica(TipoAppuntamento tipo) {
        Dialog d = new Dialog();
        d.setHeaderTitle(tipo.getId() == null ? "Nuovo tipo di appuntamento" : "Modifica tipo di appuntamento");
        d.setWidth("min(520px, 100vw)");
        TextField nome = new TextField("Nome");
        TextArea descrizione = new TextArea("Descrizione per il cliente");
        descrizione.setMaxLength(300);
        IntegerField durata = new IntegerField("Durata (minuti)");
        durata.setStepButtonsVisible(true);
        durata.setStep(15);
        Checkbox attivo = new Checkbox("Prenotabile");
        BeanValidationBinder<TipoAppuntamento> binder = new BeanValidationBinder<>(TipoAppuntamento.class);
        binder.forField(nome).bind("nome");
        binder.forField(descrizione).bind("descrizione");
        binder.forField(durata).bind("durataMin");
        binder.forField(attivo).bind("attivo");
        binder.readBean(tipo);
        if (tipo.getId() == null) {
            durata.setValue(45);
            attivo.setValue(true);
        }
        FormLayout form = new FormLayout(nome, durata, descrizione, attivo);
        form.setColspan(descrizione, 2);
        d.add(form);
        Button salva = new Button("Salva", e -> {
            if (binder.writeBeanIfValid(tipo)) {
                try {
                    service.salvaTipo(tipo);
                    Avvisi.ok("Salvato");
                    d.close();
                    aggiorna();
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
