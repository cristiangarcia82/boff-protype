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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.Durate;
import jakarta.annotation.security.RolesAllowed;

/** Catalogo degli interventi con i tempi standard. Solo per il titolare. */
@Route("catalogo")
@PageTitle("Catalogo interventi | Boff Bike")
@RolesAllowed(Ruoli.TITOLARE)
public class CatalogoView extends Div {

    private final CatalogoService service;
    private final Grid<TipoIntervento> griglia = new Grid<>();

    public CatalogoView(CatalogoService service) {
        this.service = service;
        addClassName("boff-pagina");

        Button nuovo = new Button("Nuovo intervento", VaadinIcon.PLUS.create(), e -> modifica(new TipoIntervento()));
        nuovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div intestazione = new Div(new H2("Catalogo interventi"), nuovo);
        intestazione.addClassName("boff-intestazione");

        griglia.addColumn(TipoIntervento::getNome).setHeader("Intervento").setFlexGrow(4);
        griglia.addColumn(t -> Durate.formatta(t.getDurataStandardMin())).setHeader("Tempo standard").setAutoWidth(true);
        griglia.addComponentColumn(t -> {
            Span s = new Span(t.isAttivo() ? "Attivo" : "Non più offerto");
            s.getElement().getThemeList().add(t.isAttivo() ? "badge success" : "badge contrast");
            return s;
        }).setHeader("Stato").setAutoWidth(true);
        griglia.addItemClickListener(e -> modifica(e.getItem()));
        griglia.setAllRowsVisible(true);

        add(intestazione,
                new Paragraph("I tempi standard servono a pianificare l'officina; su ogni lavoro si possono modificare. "
                        + "Clicca su una riga per modificarla."),
                griglia);
        aggiorna();
    }

    private void aggiorna() {
        griglia.setItems(service.tutti());
    }

    private void modifica(TipoIntervento tipo) {
        Dialog d = new Dialog();
        d.setHeaderTitle(tipo.getId() == null ? "Nuovo intervento" : "Modifica intervento");
        d.setWidth("min(520px, 100vw)");
        TextField nome = new TextField("Nome");
        IntegerField durata = new IntegerField("Tempo standard (minuti)");
        durata.setStep(5);
        durata.setStepButtonsVisible(true);
        Checkbox attivo = new Checkbox("Offerto (proposto a clienti e negozio)");
        BeanValidationBinder<TipoIntervento> binder = new BeanValidationBinder<>(TipoIntervento.class);
        binder.forField(nome).bind("nome");
        binder.forField(durata).bind("durataStandardMin");
        binder.forField(attivo).bind("attivo");
        binder.setBean(tipo);
        if (tipo.getId() == null) {
            durata.setValue(30);
        }
        FormLayout form = new FormLayout(nome, durata, attivo);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        d.add(form);
        Button salva = new Button("Salva", e -> {
            if (binder.validate().isOk()) {
                try {
                    service.salva(tipo);
                    Avvisi.ok("Catalogo aggiornato");
                    d.close();
                } catch (ErroreDominio ex) {
                    Avvisi.errore(ex.getMessage());
                }
            }
        });
        salva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.addDialogCloseActionListener(e -> d.close());
        d.addOpenedChangeListener(e -> { if (!e.isOpened()) aggiorna(); });
        d.getFooter().add(new Button("Annulla", e -> d.close()), salva);
        d.open();
    }
}
