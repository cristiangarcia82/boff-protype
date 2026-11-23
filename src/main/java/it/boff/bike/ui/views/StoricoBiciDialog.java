package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.LavoroService.UltimoIntervento;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.ui.componenti.BadgeStato;
import it.boff.bike.ui.componenti.Durate;
import it.boff.bike.ui.componenti.Formati;

import java.time.LocalDate;
import java.util.List;

/**
 * Storico di una bici per il negozio: per ogni intervento l'ultima volta che è stato fatto (es. "Sostituzione
 * catena · 5 mesi fa") e, sotto, tutti i lavori con gli interventi fatti.
 */
public class StoricoBiciDialog extends Dialog {

    public StoricoBiciDialog(LavoroService lavori, Bicicletta bici, String proprietario, LocalDate oggi) {
        setHeaderTitle("Storico · " + bici.getNome());
        setWidth("min(640px, 100vw)");
        addClassName("boff-storico-bici");
        Paragraph chi = new Paragraph(proprietario);
        chi.addClassName("boff-testo-secondario");
        add(chi, new H4("Ultima volta per intervento"), ultimi(lavori.ultimiInterventi(bici.getId()), oggi),
                new H4("Lavori"), elencoLavori(lavori.storicoBici(bici.getId())));
        Button chiudi = new Button("Chiudi", e -> close());
        getFooter().add(chiudi);
    }

    /** Righe compatte "intervento · quanto tempo fa · data · quante volte", la più recente prima. */
    static Component ultimi(List<UltimoIntervento> elenco, LocalDate oggi) {
        Div box = new Div();
        box.addClassName("boff-ultimi-interventi");
        if (elenco.isEmpty()) {
            Paragraph p = new Paragraph("Nessun intervento ancora fatto su questa bici.");
            p.addClassName("boff-testo-secondario");
            box.add(p);
            return box;
        }
        for (UltimoIntervento u : elenco) {
            Span nome = new Span(u.intervento());
            nome.addClassName("nome");
            Span quanto = new Span(Formati.tempoFa(u.quando(), oggi));
            quanto.addClassName("quanto");
            Span dettaglio = new Span(Formati.giornoAnno(u.quando()) + (u.volte() > 1 ? " · " + u.volte() + " volte" : ""));
            dettaglio.addClassName("boff-testo-secondario");
            box.add(new Div(nome, quanto, dettaglio));
        }
        return box;
    }

    private static Component elencoLavori(List<Lavoro> elenco) {
        Div box = new Div();
        if (elenco.isEmpty()) box.add(new Paragraph("Nessun lavoro."));
        for (Lavoro l : elenco) {
            var data = l.getProntoIl() != null ? l.getProntoIl() : l.getChiusoIl() != null ? l.getChiusoIl() : l.getAnnunciatoIl();
            RouterLink apri = new RouterLink(Formati.data(data), LavoriView.class, LavoriView.parametri(l.getId()));
            com.vaadin.flow.component.html.Anchor foglio = new com.vaadin.flow.component.html.Anchor(
                    "foglio-lavoro/" + l.getId(), "Foglio lavori");
            foglio.setTarget("_blank");
            Div sinistra = new Div(apri, new Span(" · "), foglio);
            Div testa = new Div(sinistra, new BadgeStato(l.getStato()));
            testa.addClassName("boff-intestazione");
            Div voce = new Div(testa);
            for (RigaLavoro r : l.getRighe()) {
                Div riga = new Div();
                riga.addClassName("boff-riga-icona");
                riga.add(r.isCompletata() ? VaadinIcon.CHECK.create() : VaadinIcon.CIRCLE_THIN.create());
                riga.add(new Span(r.getDescrizione() + " · " + Durate.formatta(r.getDurataMin())));
                if (!r.isCompletata() && l.getStato() != StatoLavoro.ANNULLATO && !l.getStato().isAperto()) {
                    riga.addClassName("boff-testo-secondario");
                }
                voce.add(riga);
            }
            if (l.getNoteCliente() != null) {
                Paragraph n = new Paragraph("Note del cliente: " + l.getNoteCliente());
                n.addClassName("boff-testo-secondario");
                voce.add(n);
            }
            voce.addClassNames("boff-card", "boff-storico");
            box.add(voce);
        }
        return box;
    }
}
