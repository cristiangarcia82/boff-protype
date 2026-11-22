package it.boff.bike.ui.views;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.lavoro.FotoService;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.pianificazione.PianificazioneService;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.BadgeStato;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.utente.UtenteService;
import jakarta.annotation.security.RolesAllowed;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lavori di officina per lo staff: elenco filtrabile a sinistra, dettaglio a destra.
 * Scorciatoie: Alt+N nuovo lavoro al banco, Alt+F cerca.
 */
@Route("lavori/:id?")
@PageTitle("Lavori | Boff Bike")
@RolesAllowed(Ruoli.STAFF)
public class LavoriView extends Div implements BeforeEnterObserver {

    private static final Map<String, Set<StatoLavoro>> FILTRI = new LinkedHashMap<>();
    static {
        FILTRI.put("In officina", StatoLavoro.IN_OFFICINA);
        FILTRI.put("Annunciati", EnumSet.of(StatoLavoro.ANNUNCIATO));
        FILTRI.put("Pronti", EnumSet.of(StatoLavoro.PRONTO));
        FILTRI.put("Tutti gli aperti", StatoLavoro.APERTI);
        FILTRI.put("Archivio", StatoLavoro.CHIUSI);
    }

    /** Valori di {@code ?scheda=} (es. dai contatori della bacheca), nell'ordine delle schede. */
    static final List<String> CHIAVI_SCHEDE = List.of("officina", "annunciati", "pronti", "aperti", "archivio");

    static com.vaadin.flow.router.QueryParameters scheda(String chiave) {
        return com.vaadin.flow.router.QueryParameters.of("scheda", chiave);
    }

    private final LavoroService service;
    private Grid.Column<Lavoro> colonnaArrivo;
    private final Grid<Lavoro> griglia = new Grid<>();
    private final Tabs schede = new Tabs();
    private final TextField cerca = new TextField();
    private final LavoroPannello pannello;
    private final Map<Tab, Set<StatoLavoro>> filtroPerScheda = new LinkedHashMap<>();
    private Long selezionato;

    public LavoriView(LavoroService service, CatalogoService catalogo, UtenteService utenti,
                      BiciclettaService biciclette, PianificazioneService pianificazione, FotoService foto,
                      it.boff.bike.funzionalita.FunzionalitaService funzionalita,
                      it.boff.bike.calendario.CalendarioService calendario) {
        this.service = service;
        this.pannello = new LavoroPannello(service, catalogo, pianificazione, foto, funzionalita, this::aggiornaElenco,
                () -> utenti.personale().stream().filter(it.boff.bike.utente.Utente::isAttivo).toList(), calendario::oggi);
        addClassName("boff-pagina");

        Button nuovo = new Button("Nuovo lavoro", VaadinIcon.PLUS.create(), e ->
                new NuovoLavoroDialog(utenti, biciclette, catalogo, service, l -> {
                    schede.setSelectedIndex(0);
                    apri(l.getId());
                }).open());
        nuovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nuovo.addClickShortcut(Key.KEY_N, KeyModifier.ALT);
        nuovo.setTooltipText("Bici portata al banco senza annuncio (Alt+N)");
        Div intestazione = new Div(new H2("Lavori"), nuovo);
        intestazione.addClassName("boff-intestazione");

        FILTRI.forEach((nome, stati) -> {
            Tab t = new Tab(nome);
            filtroPerScheda.put(t, stati);
            schede.add(t);
        });
        schede.addSelectedChangeListener(e -> aggiornaElenco());

        cerca.setPlaceholder("Cerca per bici o cliente");
        cerca.setPrefixComponent(VaadinIcon.SEARCH.create());
        cerca.setClearButtonVisible(true);
        cerca.setValueChangeMode(ValueChangeMode.LAZY);
        cerca.addValueChangeListener(e -> aggiornaElenco());
        cerca.setWidthFull();
        cerca.addFocusShortcut(Key.KEY_F, KeyModifier.ALT);

        griglia.addComponentColumn(l -> {
            Div d = new Div();
            if (l.isUrgente()) {
                var icona = VaadinIcon.EXCLAMATION_CIRCLE.create();
                icona.addClassName("boff-urgente");
                icona.setTooltipText("Urgente");
                d.add(icona, new Span(" "));
            }
            d.add(new Span(l.getBicicletta().getNome()));
            return d;
        }).setHeader("Bici").setAutoWidth(true).setFlexGrow(1);
        griglia.addColumn(l -> l.getBicicletta().getProprietario().getNomeCompleto()).setHeader("Cliente").setAutoWidth(true);
        griglia.addComponentColumn(l -> new BadgeStato(l.getStato())).setHeader("Stato").setAutoWidth(true);
        griglia.addColumn(l -> Durate.formatta(l.minutiDaFare())).setHeader("Da fare").setAutoWidth(true);
        griglia.addColumn(l -> l.getDataPromessa() != null ? Formati.giorno(l.getDataPromessa()) + " (promessa)"
                : Formati.giorno(l.getFinePrevista())).setHeader("Consegna").setAutoWidth(true);
        // per gli annunciati: se e quando il cliente porta la bici (lavoro già fissato in calendario)
        colonnaArrivo = griglia.addColumn(l -> l.getArrivoPrevisto() == null ? ""
                : "porta la bici " + Formati.giorno(l.getArrivoPrevisto().toLocalDate()) + " " + l.getArrivoPrevisto().toLocalTime())
                .setHeader("Arrivo bici").setAutoWidth(true);
        griglia.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        griglia.setHeight("min(70vh, 640px)");
        griglia.asSingleSelect().addValueChangeListener(e -> {
            if (e.isFromClient()) {
                apri(e.getValue() == null ? null : e.getValue().getId());
            }
        });

        Div elenco = new Div(schede, cerca, griglia);
        elenco.addClassName("boff-colonna");
        Div corpo = new Div(elenco, pannello);
        corpo.addClassName("boff-master-detail");
        add(intestazione, corpo);
        aggiornaElenco();
        pannello.mostra(null);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.getLocation().getQueryParameters().getSingleParameter("scheda").map(CHIAVI_SCHEDE::indexOf)
                .filter(i -> i >= 0 && i < schede.getTabCount()).ifPresent(i -> {
                    if (schede.getSelectedIndex() != i) schede.setSelectedIndex(i);
                });
        event.getRouteParameters().getLong("id").ifPresent(id -> {
            Lavoro l;
            try {
                l = service.dettaglio(id);
            } catch (it.boff.bike.common.ErroreDominio e) {
                event.forwardTo(LavoriView.class);
                return;
            }
            selezionato = id;
            pannello.mostra(id);
            // seleziona la scheda che contiene il lavoro
            filtroPerScheda.forEach((tab, stati) -> {
                if (stati.contains(l.getStato()) && !filtroPerScheda.get(schede.getSelectedTab()).contains(l.getStato())) {
                    schede.setSelectedTab(tab);
                }
            });
            aggiornaElenco();
        });
    }

    private void apri(Long id) {
        selezionato = id;
        pannello.mostra(id);
        getUI().ifPresent(ui -> ui.getPage().getHistory().replaceState(null,
                id == null ? "lavori" : "lavori/" + id));
        aggiornaElenco();
    }

    private void aggiornaElenco() {
        Set<StatoLavoro> stati = filtroPerScheda.get(schede.getSelectedTab());
        if (colonnaArrivo != null) colonnaArrivo.setVisible(stati.contains(StatoLavoro.ANNUNCIATO));
        List<Lavoro> elenco = service.cerca(stati, cerca.getValue());
        griglia.setItems(elenco);
        if (selezionato != null) {
            elenco.stream().filter(l -> l.getId().equals(selezionato)).findFirst().ifPresent(griglia::select);
        }
    }

    static RouteParameters parametri(Long id) {
        return new RouteParameters("id", String.valueOf(id));
    }
}
