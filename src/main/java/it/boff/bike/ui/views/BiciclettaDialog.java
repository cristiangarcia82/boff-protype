package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;

import java.util.function.Consumer;

/** Modulo per aggiungere o modificare una bicicletta. */
public class BiciclettaDialog extends Dialog {

    private final BeanValidationBinder<DatiBicicletta> binder = new BeanValidationBinder<>(DatiBicicletta.class);

    /**
     * @param dati      dati iniziali (vuoti per una nuova bici)
     * @param nuova     true se si sta aggiungendo una bici
     * @param salva     chiamato con i dati validati
     * @param rimuovi   chiamato dopo la conferma di rimozione (solo in modifica)
     */
    public BiciclettaDialog(DatiBicicletta dati, boolean nuova, Consumer<DatiBicicletta> salva, Runnable rimuovi) {
        setHeaderTitle(nuova ? "Nuova bici" : "Modifica bici");
        setWidth("min(560px, 100vw)");

        TextField marca = new TextField("Marca");
        marca.setPlaceholder("es. Bianchi");
        TextField modello = new TextField("Modello");
        modello.setPlaceholder("es. Impulso");
        marca.setRequiredIndicatorVisible(true);
        modello.setRequiredIndicatorVisible(true);

        Select<Tipologia> tipologia = new Select<>();
        tipologia.setLabel("Tipologia");
        tipologia.setItems(Tipologia.values());
        tipologia.setItemLabelGenerator(Tipologia::getEtichetta);
        tipologia.setPlaceholder("Scegli…");

        Checkbox elettrica = new Checkbox("A pedalata assistita (elettrica)");
        tipologia.addValueChangeListener(e -> {
            boolean ebike = e.getValue() == Tipologia.EBIKE;
            if (ebike) {
                elettrica.setValue(true);
            }
            elettrica.setEnabled(!ebike);
        });

        TextField taglia = new TextField("Taglia");
        taglia.setPlaceholder("es. M, 54, 29\"");
        TextField colore = new TextField("Colore");
        IntegerField anno = new IntegerField("Anno");
        anno.setPlaceholder("es. 2022");
        TextField numeroTelaio = new TextField("Numero di telaio");
        numeroTelaio.setHelperText("Facoltativo: si trova di solito sotto il movimento centrale");
        TextArea note = new TextArea("Note");
        note.setMaxLength(500);
        note.setPlaceholder("Modifiche, componenti particolari…");

        binder.forField(marca).bind("marca");
        binder.forField(modello).bind("modello");
        binder.forField(tipologia).bind("tipologia");
        binder.forField(elettrica).bind("elettrica");
        binder.forField(taglia).bind("taglia");
        binder.forField(colore).bind("colore");
        binder.forField(anno).bind("anno");
        binder.forField(numeroTelaio).bind("numeroTelaio");
        binder.forField(note).bind("note");
        binder.readBean(dati);

        FormLayout form = new FormLayout(marca, modello, tipologia, elettrica, taglia, colore, anno, numeroTelaio, note);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("440px", 2));
        form.setColspan(elettrica, 2);
        form.setColspan(numeroTelaio, 2);
        form.setColspan(note, 2);
        add(form);

        Button salvaBtn = new Button("Salva", e -> {
            DatiBicicletta risultato = new DatiBicicletta();
            if (binder.writeBeanIfValid(risultato)) {
                salva.accept(risultato);
                close();
            }
        });
        salvaBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button annulla = new Button("Annulla", e -> close());

        if (!nuova && rimuovi != null) {
            Button rimuoviBtn = new Button("Rimuovi", e -> conferma(rimuovi));
            rimuoviBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            Div spazio = new Div();
            spazio.getStyle().set("flex-grow", "1");
            getFooter().add(rimuoviBtn, spazio);
        }
        getFooter().add(annulla, salvaBtn);
        marca.focus();
    }

    private void conferma(Runnable rimuovi) {
        ConfirmDialog conferma = new ConfirmDialog("Rimuovere questa bici?",
                "Non comparirà più nel tuo elenco.", "Rimuovi", e -> {
                    rimuovi.run();
                    close();
                });
        conferma.setConfirmButtonTheme("error primary");
        conferma.setCancelable(true);
        conferma.setCancelText("Annulla");
        conferma.open();
    }
}
