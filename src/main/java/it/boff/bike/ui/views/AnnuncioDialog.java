package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.ui.componenti.Avvisi;

import java.util.List;
import java.util.function.BiConsumer;

/** Il cliente (o il negozio) indica gli interventi da fare su una bici. */
public class AnnuncioDialog extends Dialog {

    public AnnuncioDialog(String titolo, String spiegazione, List<TipoIntervento> catalogo,
                          BiConsumer<List<Long>, String> invia) {
        setHeaderTitle(titolo);
        setWidth("min(600px, 100vw)");

        CheckboxGroup<TipoIntervento> interventi = new CheckboxGroup<>("Cosa va fatto?");
        interventi.setItems(catalogo);
        interventi.setItemLabelGenerator(TipoIntervento::getNome);
        interventi.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);

        TextArea note = new TextArea("Altro o descrizione del problema");
        note.setPlaceholder("es. rumore quando freno, il cambio salta sul pignone più grande…");
        note.setMaxLength(LavoroService.MAX_NOTE);
        note.setWidthFull();

        add(new Paragraph(spiegazione), interventi, note);

        Button ok = new Button("Invia", e -> {
            try {
                invia.accept(interventi.getValue().stream().map(TipoIntervento::getId).toList(), note.getValue());
                close();
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Annulla", e -> close()), ok);
    }
}
