package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;

/** Testi in italiano per il caricamento delle foto. */
public final class CaricamentoFoto {

    private CaricamentoFoto() {
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
