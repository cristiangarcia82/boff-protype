package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.calendario.CalendarioService;
import it.boff.bike.calendario.FasciaOraria;
import it.boff.bike.calendario.NuovaFascia;
import it.boff.bike.calendario.TipoFascia;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orario settimanale tipo di ogni persona, diviso in fasce di VENDITA (banco) e
 * di OFFICINA (manutenzione). Solo il titolare lo modifica.
 */
@Route("orari")
@PageTitle("Orari | Boff Bike")
@RolesAllowed(Ruoli.TITOLARE)
public class OrariView extends Div {

    private final CalendarioService calendario;
    private final Map<Tab, Utente> persone = new LinkedHashMap<>();
    private final Tabs schede = new Tabs();
    private final Div settimana = new Div();
    private final Span riepilogo = new Span();
    private final Map<DayOfWeek, Div> righePerGiorno = new EnumMap<>(DayOfWeek.class);
    private final Map<DayOfWeek, List<RigaFascia>> fasce = new EnumMap<>(DayOfWeek.class);

    /** Editor di una fascia oraria. */
    private final class RigaFascia extends Div {
        final TimePicker inizio = orario("Dalle");
        final TimePicker fine = orario("Alle");
        final Select<TipoFascia> tipo = new Select<>();

        RigaFascia(DayOfWeek giorno, LocalTime da, LocalTime a, TipoFascia t) {
            tipo.setItems(TipoFascia.values());
            tipo.setItemLabelGenerator(TipoFascia::getEtichetta);
            tipo.setAriaLabel("Tipo");
            inizio.setValue(da);
            fine.setValue(a);
            // le fasce di vendita fisse non ci sono più: ogni fascia è orario di lavoro
            tipo.setValue(TipoFascia.MANUTENZIONE);
            tipo.setVisible(false);
            inizio.addValueChangeListener(e -> aggiornaRiepilogo());
            fine.addValueChangeListener(e -> aggiornaRiepilogo());
            tipo.addValueChangeListener(e -> { aggiornaClasse(); aggiornaRiepilogo(); });
            Button rimuovi = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> {
                fasce.get(giorno).remove(this);
                righePerGiorno.get(giorno).remove(this);
                aggiornaRiepilogo();
            });
            rimuovi.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            rimuovi.setAriaLabel("Rimuovi fascia");
            add(inizio, fine, tipo, rimuovi);
            addClassName("boff-fascia");
            aggiornaClasse();
        }

        private void aggiornaClasse() {
            setClassName("vendita", tipo.getValue() == TipoFascia.VENDITA);
            setClassName("officina", tipo.getValue() == TipoFascia.MANUTENZIONE);
        }

        NuovaFascia valore(DayOfWeek giorno) {
            return new NuovaFascia(giorno, inizio.getValue(), fine.getValue(), tipo.getValue());
        }
    }

    public OrariView(CalendarioService calendario, UtenteService utenti) {
        this.calendario = calendario;
        addClassName("boff-pagina");

        for (Utente u : utenti.personale()) {
            if (!u.isAttivo()) continue;
            Tab t = new Tab(u.getNomeCompleto());
            persone.put(t, u);
            schede.add(t);
        }
        schede.addSelectedChangeListener(e -> carica());

        Button salva = new Button("Salva orario", VaadinIcon.CHECK.create(), e -> salva());
        salva.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div intestazione = new Div(new H2("Orari settimanali"), salva);
        intestazione.addClassName("boff-intestazione");

        Paragraph spiegazione = new Paragraph("Per ogni persona indica quando lavora. In queste ore l'app mette "
                + "i lavori di officina e gli appuntamenti di consulenza. Il tempo al banco lo aggiungi tu dal "
                + "calendario, giorno per giorno (\"Turno di vendita\"). Giorni liberi, ferie e chiusure si "
                + "inseriscono in \"Assenze e chiusure\".");
        spiegazione.addClassName("boff-testo-secondario");
        riepilogo.addClassName("boff-riepilogo");
        settimana.addClassName("boff-settimana-orari");

        add(intestazione, spiegazione, schede, riepilogo, settimana);
        carica();
    }

    private Utente personaSelezionata() {
        return persone.get(schede.getSelectedTab());
    }

    private void carica() {
        settimana.removeAll();
        righePerGiorno.clear();
        fasce.clear();
        Utente p = personaSelezionata();
        if (p == null) {
            settimana.add(new Paragraph("Nessuna persona attiva nel personale."));
            return;
        }
        List<FasciaOraria> esistenti = calendario.orarioDi(p.getId());
        for (DayOfWeek g : DayOfWeek.values()) {
            fasce.put(g, new ArrayList<>());
            Div righe = new Div();
            righe.addClassName("boff-fasce");
            righePerGiorno.put(g, righe);

            Button aggiungi = new Button("Fascia", VaadinIcon.PLUS.create(), e -> {
                List<RigaFascia> elenco = fasce.get(g);
                LocalTime da = elenco.isEmpty() ? LocalTime.of(9, 0) : elenco.get(elenco.size() - 1).fine.getValue();
                if (da == null) da = LocalTime.of(9, 0);
                LocalTime a = da.plusHours(3).isBefore(da) ? LocalTime.of(23, 45) : da.plusHours(3);
                aggiungiRiga(g, da, a, TipoFascia.MANUTENZIONE);
            });
            aggiungi.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button copia = new Button("Copia dal giorno prima", e -> copiaDalGiornoPrima(g));
            copia.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            copia.setVisible(g != DayOfWeek.MONDAY);

            Span nome = new Span(nomeGiorno(g));
            nome.addClassName("boff-giorno-nome");
            Div giorno = new Div(nome, righe, new Div(aggiungi, copia));
            giorno.addClassNames("boff-card", "boff-giorno-orari");
            settimana.add(giorno);
        }
        esistenti.forEach(f -> aggiungiRiga(f.getGiorno(), f.getOraInizio(), f.getOraFine(), f.getTipo()));
        aggiornaRiepilogo();
    }

    private void aggiungiRiga(DayOfWeek g, LocalTime da, LocalTime a, TipoFascia tipo) {
        RigaFascia r = new RigaFascia(g, da, a, tipo);
        fasce.get(g).add(r);
        righePerGiorno.get(g).add(r);
        aggiornaRiepilogo();
    }

    private void copiaDalGiornoPrima(DayOfWeek g) {
        DayOfWeek prima = g.minus(1);
        fasce.get(g).clear();
        righePerGiorno.get(g).removeAll();
        new ArrayList<>(fasce.get(prima)).forEach(r ->
                aggiungiRiga(g, r.inizio.getValue(), r.fine.getValue(), r.tipo.getValue()));
    }

    private void aggiornaRiepilogo() {
        long vendita = 0, officina = 0;
        for (var e : fasce.entrySet()) {
            for (RigaFascia r : e.getValue()) {
                if (r.inizio.getValue() == null || r.fine.getValue() == null || !r.fine.getValue().isAfter(r.inizio.getValue())) continue;
                long m = Duration.between(r.inizio.getValue(), r.fine.getValue()).toMinutes();
                if (r.tipo.getValue() == TipoFascia.VENDITA) vendita += m; else officina += m;
            }
        }
        riepilogo.setText("Totale settimana: " + Durate.formatta(vendita + officina));
    }

    private void salva() {
        List<NuovaFascia> nuove = new ArrayList<>();
        fasce.forEach((g, elenco) -> elenco.forEach(r -> nuove.add(r.valore(g))));
        try {
            calendario.salvaOrario(personaSelezionata().getId(), nuove);
            Avvisi.ok("Orario di " + personaSelezionata().getNome() + " salvato");
        } catch (ErroreDominio ex) {
            Avvisi.errore(ex.getMessage());
        }
    }

    private static TimePicker orario(String etichetta) {
        TimePicker t = new TimePicker();
        t.setAriaLabel(etichetta);
        t.setPlaceholder(etichetta);
        t.setStep(Duration.ofMinutes(15));
        t.setLocale(Locale.ITALY);
        return t;
    }

    static String nomeGiorno(DayOfWeek g) {
        String n = g.getDisplayName(TextStyle.FULL, Locale.ITALY);
        return n.substring(0, 1).toUpperCase(Locale.ITALY) + n.substring(1);
    }
}
