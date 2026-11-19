package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import it.boff.bike.appuntamento.Appuntamento;
import it.boff.bike.appuntamento.AppuntamentoService;
import it.boff.bike.appuntamento.StatoAppuntamento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.CampoData;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.Utente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Dettaglio di un appuntamento per il negozio: conferma, sposta, segna svolto, annulla. */
public class AppuntamentoDialog extends Dialog {

    public AppuntamentoDialog(AppuntamentoService service, List<Utente> personale, LocalDate oggi, Long id,
                              Runnable cambiato) {
        Appuntamento a = service.dettaglio(id);
        Utente cliente = a.getCliente();
        setHeaderTitle(a.getTipo().getNome());
        setWidth("min(540px, 100vw)");

        Span stato = new Span(a.getStato().getEtichetta());
        stato.getElement().getThemeList().add(a.getStato() == StatoAppuntamento.RICHIESTO ? "badge contrast"
                : a.getStato() == StatoAppuntamento.CONFERMATO ? "badge success" : "badge");
        Paragraph quando = new Paragraph(a.getUtente().getNomeCompleto() + " · " + Formati.giorno(a.getData()) + ", "
                + a.getOraInizio() + "–" + a.getOraFine() + " ");
        quando.add(stato);
        quando.addClassName("boff-dettaglio-quando");

        Div chi = new Div(new Span("Cliente: " + cliente.getNomeCompleto()));
        chi.addClassName("boff-testo-secondario");
        if (cliente.getTelefono() != null) {
            chi.add(new Span(" · "), new Anchor("tel:" + cliente.getTelefono(), cliente.getTelefono()));
        }
        if (!cliente.usaApp()) {
            Span senzaApp = new Span("Senza app");
            senzaApp.getElement().getThemeList().add("badge contrast");
            chi.add(new Span(" "), senzaApp);
        }
        add(quando, chi);
        if (a.getBicicletta() != null) add(new Paragraph("Bici: " + a.getBicicletta().getNome()));
        if (a.getNoteCliente() != null) add(new Paragraph("Note del cliente: " + a.getNoteCliente()));
        if (a.isRichiestoDalCliente()) {
            Paragraph origine = new Paragraph("Richiesto dal cliente dall'app il " + Formati.quando(a.getCreatoIl()) + ".");
            origine.addClassName("boff-testo-secondario");
            add(origine);
        }

        TextArea note = new TextArea("Note interne");
        note.setValue(a.getNoteInterne() == null ? "" : a.getNoteInterne());
        note.setMaxLength(AppuntamentoService.MAX_NOTE);
        note.setWidthFull();
        note.setHelperText("Il cliente non le vede");
        add(note);

        boolean attivo = a.isAttivo();
        if (attivo) {
            Select<Utente> persona = new Select<>();
            persona.setLabel("Con");
            persona.setItems(personale);
            persona.setItemLabelGenerator(Utente::getNomeCompleto);
            personale.stream().filter(u -> u.getId().equals(a.getUtente().getId())).findFirst().ifPresent(persona::setValue);
            DatePicker giorno = CampoData.crea("Giorno");
            giorno.setMin(oggi);
            giorno.setValue(a.getData());
            Select<LocalTime> ora = new Select<>();
            ora.setLabel("Ora");
            ora.setEmptySelectionAllowed(false);
            Runnable aggiornaOre = () -> {
                if (persona.getValue() == null || giorno.getValue() == null) return;
                List<LocalTime> liberi = service.orariLiberiDi(persona.getValue().getId(), giorno.getValue(),
                        (int) a.intervallo().minuti(), a.getId());
                ora.setItems(liberi);
                ora.setPlaceholder(liberi.isEmpty() ? "Nessun orario libero in vendita" : "Scegli…");
                if (liberi.contains(a.getOraInizio()) && giorno.getValue().equals(a.getData())) ora.setValue(a.getOraInizio());
            };
            persona.addValueChangeListener(e -> aggiornaOre.run());
            giorno.addValueChangeListener(e -> aggiornaOre.run());
            aggiornaOre.run();
            Button sposta = new Button("Sposta", e -> esegui(() -> service.sposta(a.getId(), persona.getValue().getId(),
                    giorno.getValue(), ora.getValue()), "Appuntamento spostato: il cliente riceve un avviso", cambiato));
            sposta.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            Div riga = new Div(persona, giorno, ora, sposta);
            riga.addClassName("boff-modulo-riga");
            add(new Details("Sposta", riga));
        }

        Button chiudi = new Button("Chiudi", e -> {
            salvaNote(service, a, note.getValue());
            close();
        });
        getFooter().add(chiudi);
        if (attivo) {
            Button annulla = new Button("Annulla appuntamento", e -> esegui(() -> service.annulla(a.getId()),
                    "Appuntamento annullato", cambiato));
            annulla.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            Button svolto = new Button("Svolto", e -> esegui(() -> service.segnaSvolto(a.getId()), "Segnato come svolto", cambiato));
            svolto.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            getFooter().addComponentAsFirst(annulla);
            getFooter().add(svolto);
            if (a.getStato() == StatoAppuntamento.RICHIESTO) {
                Button conferma = new Button("Conferma", e -> {
                    salvaNote(service, a, note.getValue());
                    esegui(() -> service.conferma(a.getId()), "Confermato: il cliente riceve un avviso", cambiato);
                });
                conferma.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                getFooter().add(conferma);
            }
        }
    }

    private static void salvaNote(AppuntamentoService service, Appuntamento a, String note) {
        String prima = a.getNoteInterne() == null ? "" : a.getNoteInterne();
        if (!prima.equals(note == null ? "" : note.trim())) service.impostaNoteInterne(a.getId(), note);
    }

    private void esegui(Runnable azione, String ok, Runnable cambiato) {
        try {
            azione.run();
            Avvisi.ok(ok);
            close();
            cambiato.run();
        } catch (ErroreDominio | NullPointerException ex) {
            Avvisi.errore(ex instanceof ErroreDominio ? ex.getMessage() : "Scegli persona, giorno e ora");
        }
    }
}
