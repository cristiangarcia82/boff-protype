package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.server.streams.InMemoryUploadCallback;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.server.streams.UploadRejectedException;
import it.boff.bike.lavoro.FotoService;

/** Caricamento delle foto: testi in italiano e limiti verificati dal server. */
public final class CaricamentoFoto {

    private CaricamentoFoto() {
    }

    /**
     * Gestore che rifiuta i file troppo grandi (o di dimensione non dichiarata) PRIMA di
     * leggerli in memoria: il limite impostato sul componente vale solo nel browser e
     * una richiesta costruita a mano potrebbe aggirarlo.
     */
    public static UploadHandler gestore(InMemoryUploadCallback callback) {
        return UploadHandler.inMemory(callback).validateMetadata(evento -> {
            if (!dimensioneAmmessa(evento.getFileSize())) {
                throw new UploadRejectedException("Foto troppo grande (massimo 8 MB)");
            }
        });
    }

    static boolean dimensioneAmmessa(long byte_) {
        return byte_ > 0 && byte_ <= FotoService.MAX_BYTE;
    }

    public static void italiano(Upload upload) {
        UploadI18N i18n = new UploadI18N();
        i18n.setDropFiles(new UploadI18N.DropFiles().setOne("Trascina qui la foto").setMany("Trascina qui le foto"));
        i18n.setAddFiles(new UploadI18N.AddFiles().setOne("Aggiungi foto").setMany("Aggiungi foto"));
        i18n.setError(new UploadI18N.Error()
                .setTooManyFiles("Troppe foto")
                .setFileIsTooBig("Foto troppo grande (massimo 8 MB)")
                .setIncorrectFileType("Puoi caricare solo immagini"));
        upload.setI18n(i18n);
    }
}
