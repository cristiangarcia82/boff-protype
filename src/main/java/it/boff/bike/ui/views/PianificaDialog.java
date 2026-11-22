package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.pianificazione.Assegnazione;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.pianificazione.PianificazioneService.GiornoLibero;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.Utente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * "Quando lo facciamo?": si sceglie chi, il giorno (con il tempo libero di ciascun giorno) e l'ora da cui
 * il lavoro sta tutto di seguito. Tre tocchi, niente campi da compilare. Il lavoro resta fissato lì.
 */
public class PianificaDialog extends Dialog {

    static final int GIORNI = 14;

    private final PianificazioneService pianificazione;
    private final List<Assegnazione> pezzi;
    /** Non null: si fissa tutto il lavoro (anche annunciato, anche senza interventi in calendario). */
    private final Lavoro interoLavoro;
    private final long durata;
    private final LocalDate oggi;
    private final Runnable fatto;
    private final Div persone = new Div();
    private final Div giorni = new Div();
    private final Div ore = new Div();
    private final Paragraph suggerimento = new Paragraph();
    private Utente persona;
    private LocalDate giorno;
    private final boolean annunciato;
    private final Utente cliente;

    /** Sposta solo gli interventi indicati (es. un blocco del calendario). */
    public PianificaDialog(PianificazioneService pianificazione, Lavoro lavoro, List<Assegnazione> pezzi,
                           List<Utente> personale, LocalDate oggi, Runnable fatto) {
        this(pianificazione, lavoro, pezzi, personale, oggi, fatto, false);
    }

    /** Fissa tutto il lavoro: gli interventi da oggi in poi, o tutti quelli da fare se non è ancora in calendario. */
    public static PianificaDialog perIlLavoro(PianificazioneService pianificazione, Lavoro lavoro, List<Assegnazione> pezziDaOggi,
                                              List<Utente> personale, LocalDate oggi, Runnable fatto) {
        return new PianificaDialog(pianificazione, lavoro, pezziDaOggi, personale, oggi, fatto, true);
    }

    private PianificaDialog(PianificazioneService pianificazione, Lavoro lavoro, List<Assegnazione> pezzi,
                            List<Utente> personale, LocalDate oggi, Runnable fatto, boolean intero) {
        this.pianificazione = pianificazione;
        this.pezzi = pezzi;
        this.interoLavoro = intero ? lavoro : null;
        this.durata = !pezzi.isEmpty() ? pezzi.stream().mapToLong(a -> a.intervallo().minuti()).sum()
                : lavoro.getRighe().stream().filter(r -> !r.isCompletata() && r.isValida()).mapToLong(r -> r.getDurataMin()).sum();
        this.oggi = oggi;
        this.fatto = fatto;
        this.annunciato = lavoro.getStato() == it.boff.bike.lavoro.StatoLavoro.ANNUNCIATO;
        this.cliente = lavoro.getBicicletta().getProprietario();
        setHeaderTitle("Quando lo facciamo?");
        setWidth("min(640px, 100vw)");
        addClassName("boff-pianifica");

        Paragraph cosa = new Paragraph(lavoro.getBicicletta().getNome() + " · " + lavoro.getBicicletta().getProprietario().getNomeCompleto()
                + " · " + Durate.formatta(durata) + " di lavoro");
        cosa.addClassName("boff-dettaglio-quando");
        add(cosa);
        if (lavoro.getStato() == it.boff.bike.lavoro.StatoLavoro.ANNUNCIATO) {
            Paragraph arrivo = new Paragraph("La bici non è ancora in negozio: il cliente riceverà giorno e ora in cui portarla"
                    + (lavoro.getBicicletta().getProprietario().usaApp() ? "." : " (non usa l'app: glielo dirai tu per telefono)."));
            arrivo.addClassName("boff-testo-secondario");
            add(arrivo);
        }
        if (lavoro.getDataPromessa() != null) {
            Paragraph promessa = new Paragraph("Promessa al cliente per " + Formati.giorno(lavoro.getDataPromessa()) + ".");
            promessa.addClassName("boff-urgente");
            add(promessa);
        }

        persone.addClassName("boff-scelte");
        giorni.addClassNames("boff-scelte", "boff-scelte-giorni");
        ore.addClassName("boff-scelte");
        suggerimento.addClassName("boff-testo-secondario");
        add(new H4("1. Chi"), persone, new H4("2. Che giorno"), giorni, new H4("3. A che ora"), suggerimento, ore);

        Utente attuale = pezzi.isEmpty() ? null : pezzi.get(0).getUtente();
        persona = personale.stream().filter(u -> attuale != null && u.getId().equals(attuale.getId())).findFirst()
                .orElse(personale.isEmpty() ? null : personale.get(0));
        giorno = pezzi.isEmpty() || pezzi.get(0).getData().isBefore(oggi) ? null : pezzi.get(0).getData();
        for (Utente u : personale) {
            Button b = new Button(u.getNome(), e -> {
                persona = u;
                aggiornaPersone(personale);
                aggiornaGiorni();
            });
            b.getElement().setAttribute("data-id", String.valueOf(u.getId()));
            persone.add(b);
        }
        aggiornaPersone(personale);
        aggiornaGiorni();

        Button annulla = new Button("Indietro", e -> close());
        getFooter().add(annulla);
    }

    private void aggiornaPersone(List<Utente> personale) {
        persone.getChildren().forEach(c -> {
            Button b = (Button) c;
            boolean scelto = persona != null && Objects.equals(b.getElement().getAttribute("data-id"), String.valueOf(persona.getId()));
            seleziona(b, scelto);
        });
    }

    private void aggiornaGiorni() {
        giorni.removeAll();
        if (persona == null) return;
        List<GiornoLibero> spazi = pianificazione.spaziLiberi(pezzi.stream().map(Assegnazione::getId).toList(), persona.getId(), oggi, GIORNI, durata);
        if (giorno != null && spazi.stream().noneMatch(s -> s.giorno().equals(giorno) && s.minutiLiberi() > 0)) giorno = null;
        for (GiornoLibero s : spazi) {
            if (s.minutiLiberi() == 0) continue; // non lavora o è tutto pieno
            Span data = new Span(AppuntamentoDialog.capitale(Formati.giorno(s.giorno())));
            Span libero = new Span(Durate.formatta(s.minutiLiberi()) + " liberi");
            libero.addClassName("boff-scelta-nota");
            Button b = new Button();
            b.getElement().appendChild(data.getElement(), libero.getElement());
            b.addClickListener(e -> {
                giorno = s.giorno();
                aggiornaGiorni();
            });
            seleziona(b, s.giorno().equals(giorno));
            giorni.add(b);
        }
        if (giorni.getComponentCount() == 0) {
            Span nessuno = new Span(persona.getNome() + " non ha ore di lavoro libere nei prossimi " + GIORNI + " giorni.");
            nessuno.addClassName("boff-testo-secondario");
            giorni.add(nessuno);
        }
        aggiornaOre(spazi);
    }

    private void aggiornaOre(List<GiornoLibero> spazi) {
        ore.removeAll();
        if (giorno == null) {
            suggerimento.setText("Scegli prima un giorno.");
            return;
        }
        GiornoLibero s = spazi.stream().filter(x -> x.giorno().equals(giorno)).findFirst().orElse(null);
        List<LocalTime> inizi = s == null ? List.of() : s.inizi();
        if (inizi.isEmpty()) {
            suggerimento.setText("Non ci sono " + Durate.formatta(durata) + " di seguito: lo dividiamo negli spazi liberi della giornata.");
            Button primo = new Button("Mettilo negli spazi liberi", VaadinIcon.CHECK.create(), e -> salva(null));
            primo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            ore.add(primo);
            return;
        }
        suggerimento.setText("Orari da cui ci sono " + Durate.formatta(durata) + " liberi di seguito. Tocca per fissarlo.");
        for (LocalTime t : inizi) {
            Button b = new Button(t.toString(), e -> salva(t));
            b.addThemeVariants(ButtonVariant.LUMO_SMALL);
            ore.add(b);
        }
    }

    private void salva(LocalTime ora) {
        try {
            if (interoLavoro != null) pianificazione.fissaLavoro(interoLavoro.getId(), persona.getId(), giorno, ora);
            else pianificazione.spostaTutti(pezzi.stream().map(Assegnazione::getId).toList(), persona.getId(), giorno, ora);
            Avvisi.ok("Fissato: " + persona.getNome() + ", " + Formati.giorno(giorno) + (ora == null ? "" : " alle " + ora)
                    + (annunciato ? (cliente.usaApp() ? ". Il cliente riceve giorno e ora in cui portare la bici"
                    : ". Il cliente non usa l'app: chiamalo per dirgli quando portare la bici"
                    + (cliente.getTelefono() == null ? "" : " (" + cliente.getTelefono() + ")")) : ""));
            close();
            fatto.run();
        } catch (ErroreDominio ex) {
            Avvisi.errore(ex.getMessage());
            aggiornaGiorni();
        }
    }

    private static void seleziona(Button b, boolean scelto) {
        b.removeThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
        b.addThemeVariants(scelto ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_CONTRAST);
        b.getElement().setAttribute("aria-pressed", String.valueOf(scelto));
    }
}
