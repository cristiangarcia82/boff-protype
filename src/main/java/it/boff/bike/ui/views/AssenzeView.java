package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.calendario.Assenza;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.MotivoAssenza;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.CampoData;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDate;

/** Ferie, giorni liberi e chiusure del negozio. Titolare e collaboratore. */
@Route("assenze")
@PageTitle("Assenze e chiusure | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class AssenzeView extends Div {

    private final CalendarioService calendario;
    private final Div elencoAssenze = new Div();
    private final Div elencoChiusure = new Div();

    public AssenzeView(CalendarioService calendario, UtenteService utenti) {
        this.calendario = calendario;
        addClassName("boff-pagina");

        // --- assenze ---
        Select<Utente> persona = new Select<>();
        persona.setLabel("Persona");
        persona.setItems(utenti.personale().stream().filter(Utente::isAttivo).toList());
        persona.setItemLabelGenerator(Utente::getNomeCompleto);
        DatePicker dal = CampoData.crea("Dal");
        DatePicker al = CampoData.crea("Al (compreso)");
        dal.addValueChangeListener(e -> { if (al.getValue() == null || (e.getValue() != null && al.getValue().isBefore(e.getValue()))) al.setValue(e.getValue()); });
        Select<MotivoAssenza> motivo = new Select<>();
        motivo.setLabel("Motivo");
        motivo.setItems(MotivoAssenza.values());
        motivo.setItemLabelGenerator(MotivoAssenza::getEtichetta);
        motivo.setValue(MotivoAssenza.RIPOSO);
        TextField note = new TextField("Note");
        Checkbox parziale = new Checkbox("Solo una parte della giornata");
        TimePicker dalle = orario("Dalle");
        TimePicker alle = orario("Alle");
        dalle.setVisible(false);
        alle.setVisible(false);
        parziale.addValueChangeListener(e -> {
            dalle.setVisible(e.getValue());
            alle.setVisible(e.getValue());
            al.setEnabled(!e.getValue());
            if (e.getValue()) al.setValue(dal.getValue());
        });
        dal.addValueChangeListener(e -> { if (parziale.getValue()) al.setValue(e.getValue()); });
        Button aggiungiAssenza = new Button("Aggiungi assenza", VaadinIcon.PLUS.create(), e -> {
            if (persona.getValue() == null) {
                Avvisi.errore("Scegli la persona");
                return;
            }
            try {
                calendario.aggiungiAssenza(persona.getValue().getId(), dal.getValue(), al.getValue(),
                        parziale.getValue() ? dalle.getValue() : null, parziale.getValue() ? alle.getValue() : null,
                        motivo.getValue(), note.getValue());
                Avvisi.ok("Assenza registrata");
                note.clear();
                aggiorna();
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        aggiungiAssenza.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div moduloAssenza = new Div(persona, dal, al, motivo, parziale, dalle, alle, note, aggiungiAssenza);
        moduloAssenza.addClassName("boff-modulo-riga");
        elencoAssenze.addClassName("boff-colonna");
        Div schedaAssenze = new Div(new H3("Assenze del personale"),
                testo("Ferie, giorni liberi, malattia: in quei giorni la persona non viene considerata nella pianificazione."),
                moduloAssenza, elencoAssenze);
        schedaAssenze.addClassName("boff-card");

        // --- chiusure ---
        DatePicker giorno = CampoData.crea("Giorno");
        TextField motivoChiusura = new TextField("Motivo");
        motivoChiusura.setPlaceholder("es. Santo patrono, inventario");
        Button aggiungiChiusura = new Button("Aggiungi chiusura", VaadinIcon.PLUS.create(), e -> {
            try {
                calendario.aggiungiChiusura(giorno.getValue(), motivoChiusura.getValue());
                Avvisi.ok("Chiusura registrata");
                motivoChiusura.clear();
                aggiorna();
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        aggiungiChiusura.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div moduloChiusura = new Div(giorno, motivoChiusura, aggiungiChiusura);
        moduloChiusura.addClassName("boff-modulo-riga");
        elencoChiusure.addClassName("boff-colonna");
        Div schedaChiusure = new Div(new H3("Chiusure del negozio"),
                testo("Le festività nazionali sono già incluse. Aggiungi le altre chiusure (patrono, ferie del negozio…)."),
                moduloChiusura, elencoChiusure);
        schedaChiusure.addClassName("boff-card");

        Div corpo = new Div(schedaAssenze, schedaChiusure);
        corpo.addClassName("boff-due-colonne");
        add(new H2("Assenze e chiusure"), corpo);
        aggiorna();
    }

    private void aggiorna() {
        LocalDate oggi = calendario.oggi();
        elencoAssenze.removeAll();
        var assenze = calendario.assenzeDa(oggi);
        if (assenze.isEmpty()) elencoAssenze.add(testo("Nessuna assenza in programma."));
        for (Assenza a : assenze) {
            String periodo = a.getDal().equals(a.getAl()) ? Formati.giorno(a.getDal())
                    : Formati.giorno(a.getDal()) + " → " + Formati.giorno(a.getAl());
            periodo += a.orario().map(o -> ", " + o.inizio() + "–" + o.fine()).orElse("");
            Button elimina = new Button(VaadinIcon.TRASH.create(), e -> {
                calendario.rimuoviAssenza(a.getId());
                Avvisi.ok("Assenza eliminata");
                aggiorna();
            });
            elimina.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
            elimina.setAriaLabel("Elimina assenza");
            elencoAssenze.add(voce(a.getUtente().getNomeCompleto() + " · " + periodo,
                    a.getMotivo().getEtichetta() + (a.getNote() == null ? "" : " – " + a.getNote()), elimina));
        }

        elencoChiusure.removeAll();
        for (var c : calendario.giorniChiusi(oggi, oggi.plusMonths(12))) {
            Button elimina = new Button(VaadinIcon.TRASH.create(), e -> {
                calendario.rimuoviChiusura(c.chiusuraId());
                Avvisi.ok("Chiusura eliminata");
                aggiorna();
            });
            elimina.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
            elimina.setAriaLabel("Elimina chiusura");
            elimina.setVisible(c.chiusuraId() != null);
            elencoChiusure.add(voce(Formati.giornoAnno(c.data()),
                    c.descrizione() + (c.chiusuraId() == null ? " (festività)" : ""), elimina));
        }
    }

    private static Div voce(String titolo, String dettaglio, Button azione) {
        Span t = new Span(titolo);
        t.addClassName("boff-bici-nome-piccolo");
        Span d = new Span(dettaglio);
        d.addClassName("boff-testo-secondario");
        Div testi = new Div(t, d);
        testi.addClassName("boff-colonna");
        testi.getStyle().set("gap", "0");
        Div v = new Div(testi, azione);
        v.addClassName("boff-voce-azione");
        return v;
    }

    private static TimePicker orario(String etichetta) {
        TimePicker t = new TimePicker(etichetta);
        t.setStep(java.time.Duration.ofMinutes(15));
        t.setLocale(java.util.Locale.ITALY);
        return t;
    }

    private static Paragraph testo(String t) {
        Paragraph p = new Paragraph(t);
        p.addClassName("boff-testo-secondario");
        return p;
    }
}
