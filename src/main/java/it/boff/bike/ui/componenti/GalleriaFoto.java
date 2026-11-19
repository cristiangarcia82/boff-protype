package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.UploadHandler;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.FotoInfo;
import it.boff.bike.lavoro.FotoService;

import java.io.ByteArrayInputStream;
import java.util.function.BiConsumer;

/**
 * Miniature delle foto di un lavoro (tocca per ingrandire) e, se previsto, pulsante per
 * aggiungerne. Le immagini sono servite solo a chi vede il componente (sessione dell'utente).
 */
public class GalleriaFoto extends Div {

    private final FotoService foto;
    private final Long lavoroId;
    private final boolean puoEliminare;
    private final Div miniature = new Div();

    /**
     * @param aggiungi se non null mostra il pulsante di caricamento: riceve (nome file, contenuto)
     */
    public GalleriaFoto(FotoService foto, Long lavoroId, boolean puoEliminare, BiConsumer<String, byte[]> aggiungi) {
        this.foto = foto;
        this.lavoroId = lavoroId;
        this.puoEliminare = puoEliminare;
        addClassName("boff-galleria");
        miniature.addClassName("boff-miniature");
        add(miniature);
        if (aggiungi != null) {
            Upload upload = new Upload(it.boff.bike.ui.componenti.CaricamentoFoto.gestore((meta, dati) -> getUI().ifPresent(ui -> ui.access(() -> {
                try {
                    aggiungi.accept(meta.fileName(), dati);
                    aggiorna();
                } catch (ErroreDominio ex) {
                    Avvisi.errore(ex.getMessage());
                }
            }))));
            upload.setAcceptedFileTypes("image/*");
            it.boff.bike.ui.componenti.CaricamentoFoto.italiano(upload);
            upload.setMaxFileSize(FotoService.MAX_BYTE);
            upload.setDropAllowed(true);
            upload.setUploadButton(new Button("Aggiungi foto", VaadinIcon.CAMERA.create()));
            upload.addAllFinishedListener(e -> upload.clearFileList());
            upload.addFileRejectedListener(e -> Avvisi.errore("Foto non accettata: massimo 8 MB, solo immagini"));
            add(upload);
        }
        aggiorna();
    }

    public void aggiorna() {
        miniature.removeAll();
        for (FotoInfo f : foto.elenco(lavoroId)) {
            Image img = new Image(scarica(f), f.nomeFile());
            img.addClassName("boff-miniatura");
            img.addClickListener(e -> ingrandisci(f));
            img.getElement().setAttribute("tabindex", "0");
            miniature.add(img);
        }
        miniature.setVisible(miniature.getComponentCount() > 0);
    }

    private DownloadHandler scarica(FotoInfo f) {
        return DownloadHandler.fromInputStream(e -> {
            byte[] dati = foto.contenuto(lavoroId, f.id());
            return new DownloadResponse(new ByteArrayInputStream(dati), f.nomeFile(), f.tipo(), dati.length);
        });
    }

    private void ingrandisci(FotoInfo f) {
        Dialog d = new Dialog();
        d.setHeaderTitle(f.dalCliente() ? "Foto del cliente" : "Foto del negozio");
        Image grande = new Image(scarica(f), f.nomeFile());
        grande.addClassName("boff-foto-grande");
        d.add(grande);
        if (puoEliminare) {
            Button elimina = new Button("Elimina", VaadinIcon.TRASH.create(), e -> {
                foto.elimina(f.id());
                d.close();
                aggiorna();
            });
            elimina.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            d.getFooter().add(elimina);
        }
        d.getFooter().add(new Button("Chiudi", e -> d.close()));
        d.open();
    }
}
