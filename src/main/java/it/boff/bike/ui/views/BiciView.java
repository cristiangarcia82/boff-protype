package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.FotoService;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.ui.componenti.GalleriaFoto;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.security.Ruoli;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.ui.componenti.BadgeStato;
import it.boff.bike.ui.componenti.Formati;
import it.boff.bike.ui.componenti.SchedaBici;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;
import java.util.Optional;

/** Dettaglio di una bici del cliente: lavoro in corso, annuncio di un nuovo lavoro, storico. */
@Route("le-mie-bici/:id")
@PageTitle("La mia bici | Boff Bike")
@RolesAllowed(Ruoli.CLIENTE)
public class BiciView extends Div implements BeforeEnterObserver {

    private final BiciclettaService biciclette;
    private final LavoroService lavori;
    private final CatalogoService catalogo;
    private final FotoService foto;
    private final Long clienteId;
    private Long biciId;

    public BiciView(BiciclettaService biciclette, LavoroService lavori, CatalogoService catalogo,
                    UtenteCorrente utenteCorrente, FotoService foto) {
        this.foto = foto;
        this.biciclette = biciclette;
        this.lavori = lavori;
        this.catalogo = catalogo;
        this.clienteId = utenteCorrente.get().orElseThrow().getId();
        addClassNames("boff-pagina", "boff-pagina-cliente", "boff-colonna");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Optional<Long> id = event.getRouteParameters().getLong("id");
        Optional<Bicicletta> bici = id.flatMap(i -> biciclette.elencoDi(clienteId).stream()
                .filter(b -> b.getId().equals(i)).findFirst());
        if (bici.isEmpty()) {
            event.forwardTo(LeMieBiciView.class);
            return;
        }
        biciId = bici.get().getId();
        mostra();
    }

    private void mostra() {
        removeAll();
        Bicicletta b = biciclette.elencoDi(clienteId).stream().filter(x -> x.getId().equals(biciId)).findFirst()
                .orElseThrow();
        List<Lavoro> storico = lavori.storicoBiciDelCliente(clienteId, biciId);
        Optional<Lavoro> aperto = storico.stream().filter(l -> l.getStato().isAperto()).findFirst();

        RouterLink indietro = new RouterLink(LeMieBiciView.class);
        indietro.add(VaadinIcon.ARROW_LEFT.create(), new Span(" Le mie bici"));
        indietro.addClassName("boff-indietro");

        Button modifica = new Button("Modifica", VaadinIcon.EDIT.create(), e -> modifica(b));
        modifica.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Div titolo = new Div(new H2(b.getNome()), modifica);
        titolo.addClassName("boff-intestazione");
        Paragraph info = new Paragraph(SchedaBici.descrizione(b));
        info.addClassName("boff-testo-secondario");
        add(indietro, titolo, info);

        if (aperto.isPresent()) {
            add(schedaLavoroInCorso(aperto.get()));
        } else {
            Button annuncia = new Button("Annuncia un lavoro", VaadinIcon.WRENCH.create(), e -> annuncia(b));
            annuncia.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
            annuncia.setWidthFull();
            Div card = new Div(new H3("Serve un intervento?"),
                    new Paragraph("Indicaci cosa fare: quando porti la bici in negozio sapremo già di cosa ha bisogno."),
                    annuncia);
            card.addClassName("boff-card");
            add(card);
        }

        List<Lavoro> chiusi = storico.stream().filter(l -> !l.getStato().isAperto()).toList();
        add(new H3("Storico interventi"));
        if (chiusi.isEmpty()) {
            add(new Paragraph("Nessun intervento passato."));
        }
        chiusi.forEach(l -> {
            Div voce = new Div(new Div(new Span(Formati.data(l.getChiusoIl())), new BadgeStato(l.getStato())),
                    elencoRighe(l, false));
            voce.addClassNames("boff-card", "boff-storico");
            add(voce);
        });
    }

    private Div schedaLavoroInCorso(Lavoro l) {
        Div card = new Div();
        card.addClassNames("boff-card", "boff-lavoro-corrente");
        Div testa = new Div(new H3("Lavoro in corso"), new BadgeStato(l.getStato()));
        testa.addClassName("boff-intestazione");
        card.add(testa, new Paragraph(messaggioPer(l)), elencoRighe(l, true));
        if (l.getNoteCliente() != null) {
            Paragraph note = new Paragraph("Le tue note: " + l.getNoteCliente());
            note.addClassName("boff-testo-secondario");
            card.add(note);
        }
        card.add(new GalleriaFoto(foto, l.getId(), false,
                (nome, dati) -> foto.aggiungiDelCliente(clienteId, l.getId(), nome, dati)));
        if (l.getStato() == StatoLavoro.ANNUNCIATO) {
            Button annulla = new Button("Annulla la richiesta", e -> {
                ConfirmDialog c = new ConfirmDialog("Annullare la richiesta?",
                        "Potrai annunciare un nuovo lavoro in qualsiasi momento.", "Annulla richiesta", ev -> {
                            lavori.annullaDelCliente(clienteId, l.getId());
                            Avvisi.ok("Richiesta annullata");
                            mostra();
                        });
                c.setCancelable(true);
                c.setCancelText("Indietro");
                c.setConfirmButtonTheme("error primary");
                c.open();
            });
            annulla.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            card.add(annulla);
        }
        return card;
    }

    private static String messaggioPer(Lavoro l) {
        return switch (l.getStato()) {
            case ANNUNCIATO -> "Abbiamo ricevuto la tua richiesta. Porta la bici in negozio quando ti è comodo.";
            case IN_NEGOZIO -> "La bici è in negozio: a breve ti diremo quando sarà pronta.";
            case PIANIFICATO, IN_LAVORAZIONE -> l.getFinePrevista() != null
                    ? "Pronta prevista per " + Formati.giorno(l.getFinePrevista()) + "."
                    : "Ci stiamo lavorando.";
            case PRONTO -> "La tua bici è pronta! Passa a ritirarla.";
            default -> l.getStato().getDescrizione();
        };
    }

    private static UnorderedList elencoRighe(Lavoro l, boolean conStato) {
        UnorderedList ul = new UnorderedList();
        ul.addClassName("boff-righe-cliente");
        for (RigaLavoro r : l.getRighe()) {
            ListItem li = new ListItem();
            if (conStato && r.isCompletata()) {
                li.add(VaadinIcon.CHECK.create());
                li.addClassName("fatto");
            }
            li.add(new Span(r.getDescrizione()));
            ul.add(li);
        }
        return ul;
    }

    private void annuncia(Bicicletta b) {
        new AnnuncioDialog("Annuncia un lavoro – " + b.getNome(),
                "Seleziona gli interventi e/o descrivi il problema. Non serve prendere appuntamento: "
                        + "porta la bici quando vuoi, organizziamo noi i lavori.",
                catalogo.attivi(), true, (tipi, note, scelte) -> {
                    Long id = lavori.annuncia(clienteId, b.getId(), tipi, note).getId();
                    int scartate = 0;
                    for (AnnuncioDialog.FotoScelta f : scelte) {
                        try {
                            foto.aggiungiDelCliente(clienteId, id, f.nome(), f.dati());
                        } catch (ErroreDominio ex) {
                            scartate++;
                        }
                    }
                    Avvisi.ok("Richiesta inviata al negozio" + (scartate > 0 ? " (" + scartate + " foto non accettate)" : ""));
                    mostra();
                }).open();
    }

    private void modifica(Bicicletta b) {
        new BiciclettaDialog(DatiBicicletta.da(b), false, dati -> {
            biciclette.modifica(clienteId, b.getId(), dati);
            Avvisi.ok("Modifiche salvate");
            mostra();
        }, () -> {
            biciclette.rimuovi(clienteId, b.getId());
            Avvisi.ok("Bici rimossa");
            getUI().ifPresent(ui -> ui.navigate(LeMieBiciView.class));
        }).open();
    }
}
