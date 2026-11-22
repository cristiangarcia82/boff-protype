package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import it.boff.bike.appuntamento.Appuntamento;
import it.boff.bike.appuntamento.AppuntamentoService;
import it.boff.bike.appuntamento.StatoAppuntamento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.Utente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Dettaglio di un appuntamento per il negozio. Mostra solo le azioni sensate per lo stato e l'ora:
 * richiesta futura → Conferma / Cambia orario / Rifiuta; confermato futuro → Cambia orario / Annulla;
 * già iniziato → Svolto. Rifiutare e annullare chiedono conferma.
 */
public class AppuntamentoDialog extends Dialog {

    public AppuntamentoDialog(AppuntamentoService service, List<Utente> personale, LocalDate oggi, Long id,
                              Runnable cambiato) {
        this(service, personale, oggi, id, cambiato, false);
    }

    public AppuntamentoDialog(AppuntamentoService service, List<Utente> personale, LocalDate oggi, Long id,
                              Runnable cambiato, boolean apriCambioOrario) {
        Appuntamento a = service.dettaglio(id);
        Utente cliente = a.getCliente();
        boolean iniziato = service.iniziato(a);
        boolean richiesta = a.getStato() == StatoAppuntamento.RICHIESTO;
        setHeaderTitle(cliente.getNomeCompleto());
        Span stato = new Span(a.getStato().getEtichetta());
        stato.getElement().getThemeList().add(richiesta ? "badge contrast"
                : a.getStato() == StatoAppuntamento.CONFERMATO ? "badge success" : "badge");
        getHeader().add(stato);
        setWidth("min(560px, 100vw)");
        addClassName("boff-dettaglio-appuntamento");

        Div info = new Div();
        info.addClassName("boff-info-griglia");
        riga(info, "Cosa", new Span(a.getTipo().getNome()));
        riga(info, "Quando", new Span(capitale(Formati.giorno(a.getData())) + ", " + a.getOraInizio() + "–" + a.getOraFine()));
        riga(info, "Con", new Span(a.getUtente().getNomeCompleto()));
        riga(info, "Telefono", telefono(cliente));
        if (a.getBicicletta() != null) riga(info, "Bici", new Span(a.getBicicletta().getNome()));
        if (a.getNoteCliente() != null) riga(info, "Note del cliente", new Span(a.getNoteCliente()));
        if (a.isRichiestoDalCliente()) riga(info, "Richiesto", new Span("dall'app il " + Formati.quando(a.getCreatoIl())));
        add(info);

        if (richiesta && iniziato) {
            Paragraph passato = new Paragraph("L'orario richiesto è già passato: proponi un altro orario o rifiuta la richiesta.");
            passato.addClassName("boff-avviso");
            add(passato);
        }

        TextArea note = new TextArea("Note interne");
        note.setValue(a.getNoteInterne() == null ? "" : a.getNoteInterne());
        note.setMaxLength(AppuntamentoService.MAX_NOTE);
        note.setWidthFull();
        note.setHelperText("Il cliente non le vede");
        Runnable salvaNote = () -> salvaNote(service, a, note.getValue());

        if (a.isAttivo()) {
            Details cambio = new Details("Cambia orario", modificaOrario(service, personale, oggi, a, () -> {
                salvaNote.run();
                close();
                cambiato.run();
            }));
            cambio.setOpened(apriCambioOrario || (richiesta && iniziato));
            add(cambio);
        }
        add(note);

        Button chiudi = new Button("Chiudi", e -> {
            salvaNote.run();
            close();
        });
        getFooter().add(chiudi);
        Runnable fatto = () -> {
            close();
            cambiato.run();
        };
        if (a.isAttivo() && !(a.getStato() == StatoAppuntamento.CONFERMATO && iniziato)) {
            Button annulla = new Button(richiesta ? "Rifiuta" : "Annulla appuntamento", e -> {
                salvaNote.run();
                chiediAnnullamento(service, a, fatto);
            });
            annulla.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            getFooter().addComponentAsFirst(annulla);
        }
        if (richiesta && !iniziato) {
            Button conferma = new Button("Conferma", VaadinIcon.CHECK.create(), e -> {
                salvaNote.run();
                conferma(service, a, fatto);
            });
            conferma.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            getFooter().add(conferma);
        }
        if (a.getStato() == StatoAppuntamento.CONFERMATO && iniziato) {
            Button svolto = new Button("Segna svolto", VaadinIcon.CHECK.create(), e -> {
                salvaNote.run();
                segnaSvolto(service, a, fatto);
            });
            svolto.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            getFooter().add(svolto);
        }
    }

    // ------------------------------------------------------------------ azioni condivise con la pagina

    static void conferma(AppuntamentoService service, Appuntamento a, Runnable fatto) {
        esegui(() -> service.conferma(a.getId()), "Confermato" + avviso(a.getCliente()), fatto);
    }

    static void segnaSvolto(AppuntamentoService service, Appuntamento a, Runnable fatto) {
        esegui(() -> service.segnaSvolto(a.getId()), "Segnato come svolto", fatto);
    }

    /** Rifiuta una richiesta o annulla un appuntamento, dopo una conferma. */
    static void chiediAnnullamento(AppuntamentoService service, Appuntamento a, Runnable fatto) {
        boolean richiesta = a.getStato() == StatoAppuntamento.RICHIESTO;
        Utente cliente = a.getCliente();
        ConfirmDialog d = new ConfirmDialog();
        d.setHeader(richiesta ? "Rifiutare la richiesta?" : "Annullare l'appuntamento?");
        d.setText(cliente.getNomeCompleto() + " · " + a.getTipo().getNome() + ", " + Formati.giorno(a.getData()) + " alle "
                + a.getOraInizio() + ". " + (cliente.usaApp() ? "Il cliente riceve un avviso."
                : "Il cliente non usa l'app: avvisalo per telefono" + (cliente.getTelefono() != null ? " (" + cliente.getTelefono() + ")." : ".")));
        d.setCancelable(true);
        d.setCancelText("Indietro");
        d.setConfirmText(richiesta ? "Rifiuta" : "Annulla appuntamento");
        d.setConfirmButtonTheme("error primary");
        d.addConfirmListener(e -> esegui(() -> service.annulla(a.getId()), richiesta ? "Richiesta rifiutata" : "Appuntamento annullato", fatto));
        d.open();
    }

    static Component telefono(Utente cliente) {
        Span s = new Span();
        if (cliente.getTelefono() != null) s.add(new Anchor("tel:" + cliente.getTelefono(), cliente.getTelefono()));
        else s.add("–");
        if (!cliente.usaApp()) {
            Span senzaApp = new Span("Senza app");
            senzaApp.getElement().getThemeList().add("badge contrast small");
            s.add(new Span(" "), senzaApp);
        }
        return s;
    }

    static String capitale(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String avviso(Utente cliente) {
        if (cliente.usaApp()) return ": il cliente riceve un avviso";
        return ": il cliente non usa l'app, avvisalo per telefono" + (cliente.getTelefono() != null ? " (" + cliente.getTelefono() + ")" : "");
    }

    private static void esegui(Runnable azione, String ok, Runnable fatto) {
        try {
            azione.run();
            Avvisi.ok(ok);
            fatto.run();
        } catch (ErroreDominio ex) {
            Avvisi.errore(ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ interni

    private static void riga(Div griglia, String etichetta, Component valore) {
        Span e = new Span(etichetta);
        e.addClassName("boff-info-etichetta");
        griglia.add(e, valore);
    }

    /** Persona, poi i giorni con posto (prossimi 14) e i loro orari liberi, tutti a pulsanti: due tocchi. */
    private static Component modificaOrario(AppuntamentoService service, List<Utente> personale, LocalDate oggi,
                                            Appuntamento a, Runnable fatto) {
        Select<Utente> persona = new Select<>();
        persona.setLabel("Con");
        persona.setItems(personale);
        persona.setItemLabelGenerator(u -> u == null ? "" : u.getNomeCompleto());
        personale.stream().filter(u -> u.getId().equals(a.getUtente().getId())).findFirst().ifPresent(persona::setValue);
        Div giorni = new Div();
        giorni.addClassNames("boff-scelte", "boff-scelte-giorni");
        Div ore = new Div();
        ore.addClassName("boff-scelte");
        LocalDate[] scelto = {null};
        Runnable[] mostraOre = new Runnable[1];
        Runnable aggiornaGiorni = () -> {
            giorni.removeAll();
            ore.removeAll();
            if (persona.getValue() == null) return;
            int durata = (int) a.intervallo().minuti();
            java.util.Map<LocalDate, List<LocalTime>> liberi = new java.util.LinkedHashMap<>();
            for (int i = 0; i < 14; i++) {
                LocalDate g = oggi.plusDays(i);
                List<LocalTime> t = service.orariLiberiDi(persona.getValue().getId(), g, durata, a.getId());
                if (!t.isEmpty()) liberi.put(g, t);
            }
            if (liberi.isEmpty()) {
                Span nessuno = new Span("Nessun orario libero nei prossimi 14 giorni: prova con un'altra persona.");
                nessuno.addClassName("boff-testo-secondario");
                giorni.add(nessuno);
                return;
            }
            if (scelto[0] == null || !liberi.containsKey(scelto[0])) {
                scelto[0] = liberi.containsKey(a.getData()) ? a.getData() : liberi.keySet().iterator().next();
            }
            mostraOre[0] = () -> {
                ore.removeAll();
                for (LocalTime t : liberi.getOrDefault(scelto[0], List.of())) {
                    boolean attuale = t.equals(a.getOraInizio()) && scelto[0].equals(a.getData())
                            && persona.getValue().getId().equals(a.getUtente().getId());
                    Button b = new Button(t.toString(), e -> esegui(() -> service.sposta(a.getId(), persona.getValue().getId(),
                            scelto[0], t), "Nuovo orario salvato" + avviso(a.getCliente()), fatto));
                    b.addThemeVariants(ButtonVariant.LUMO_SMALL);
                    if (attuale) {
                        b.setEnabled(false);
                        b.setTooltipText("Orario attuale");
                    }
                    ore.add(b);
                }
            };
            for (LocalDate g : liberi.keySet()) {
                Button b = new Button(capitale(Formati.giorno(g)));
                b.addThemeVariants(ButtonVariant.LUMO_SMALL, g.equals(scelto[0]) ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
                b.addClickListener(e -> {
                    scelto[0] = g;
                    giorni.getChildren().forEach(c -> {
                        Button x = (Button) c;
                        x.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
                        x.addThemeVariants(x == b ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
                    });
                    mostraOre[0].run();
                });
                giorni.add(b);
            }
            mostraOre[0].run();
        };
        persona.addValueChangeListener(e -> aggiornaGiorni.run());
        aggiornaGiorni.run();
        Paragraph aiuto = new Paragraph("Scegli il giorno, poi tocca l'ora: l'appuntamento si sposta e vale come confermato.");
        aiuto.addClassName("boff-testo-secondario");
        return new Div(persona, aiuto, giorni, ore);
    }

    private static void salvaNote(AppuntamentoService service, Appuntamento a, String note) {
        String prima = a.getNoteInterne() == null ? "" : a.getNoteInterne();
        if (!prima.equals(note == null ? "" : note.trim())) service.impostaNoteInterne(a.getId(), note);
    }
}
