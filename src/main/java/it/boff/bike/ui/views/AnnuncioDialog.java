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

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import it.boff.bike.lavoro.FotoService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Il cliente (o il negozio) indica gli interventi da fare su una bici. */
public class AnnuncioDialog extends Dialog {

    /** Foto scelta nel modulo, inviata insieme all'annuncio. */
    public record FotoScelta(String nome, byte[] dati) {
    }

    /** Cosa fare all'invio. */
    public interface Invio {
        void invia(List<Long> tipiIntervento, String note, List<FotoScelta> foto);
    }

    public AnnuncioDialog(String titolo, String spiegazione, List<TipoIntervento> catalogo,
                          BiConsumer<List<Long>, String> invia) {
        this(titolo, spiegazione, catalogo, false, (t, n, f) -> invia.accept(t, n));
    }

    public AnnuncioDialog(String titolo, String spiegazione, List<TipoIntervento> catalogo, boolean conFoto,
                          Invio invia) {
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

        List<FotoScelta> foto = new ArrayList<>();
        if (conFoto) {
            Span inAttesa = new Span();
            inAttesa.addClassName("boff-foto-in-attesa");
            Upload upload = new Upload(UploadHandler.inMemory((meta, dati) -> getUI().ifPresent(ui -> ui.access(() -> {
                foto.add(new FotoScelta(meta.fileName(), dati));
                inAttesa.setText(foto.size() + " foto pronte da inviare");
            }))));
            upload.setAcceptedFileTypes("image/*");
            it.boff.bike.ui.componenti.CaricamentoFoto.italiano(upload);
            upload.setMaxFiles(FotoService.MAX_CLIENTE);
            upload.setMaxFileSize(FotoService.MAX_BYTE);
            upload.setUploadButton(new Button("Aggiungi foto (facoltative)", VaadinIcon.CAMERA.create()));
            upload.addFileRejectedListener(ev -> Avvisi.errore("Foto non accettata: massimo 8 MB, solo immagini"));
            add(upload, inAttesa);
        }

        Button ok = new Button("Invia", e -> {
            try {
                invia.invia(interventi.getValue().stream().map(TipoIntervento::getId).toList(), note.getValue(), foto);
                close();
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Annulla", e -> close()), ok);
    }
}
