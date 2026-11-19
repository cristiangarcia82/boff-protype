package it.boff.bike.ui.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.ui.componenti.Avvisi;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.DatiAnagrafica;
import it.boff.bike.bicicletta.DatiBicicletta;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import it.boff.bike.utente.UtenteService;

import java.util.function.Consumer;

/** Bici portata al banco senza annuncio: il lavoro nasce già "in negozio". */
class NuovoLavoroDialog extends Dialog {

    NuovoLavoroDialog(UtenteService utenti, BiciclettaService biciclette, CatalogoService catalogo,
                      LavoroService lavori, Consumer<Lavoro> creato) {
        setHeaderTitle("Nuovo lavoro al banco");
        setWidth("min(640px, 100vw)");

        ComboBox<Utente> cliente = new ComboBox<>("Cliente");
        cliente.setItems(q -> utenti.cercaClienti(q.getFilter().orElse("")).stream()
                .skip(q.getOffset()).limit(q.getLimit()));
        cliente.setItemLabelGenerator(u -> u.getNomeCompleto() + (u.getTelefono() == null ? "" : " · " + u.getTelefono()));
        cliente.setWidthFull();
        cliente.setHelperText("Cerca per nome o telefono");

        Select<Bicicletta> bici = new Select<>();
        bici.setLabel("Bici");
        bici.setItemLabelGenerator(Bicicletta::getNome);
        bici.setEnabled(false);
        bici.setWidthFull();
        Paragraph nessunaBici = new Paragraph("Questo cliente non ha ancora bici: aggiungila qui sotto.");
        nessunaBici.addClassName("boff-testo-secondario");
        nessunaBici.setVisible(false);

        // creazione rapida, senza lasciare la finestra: utile per chi arriva in negozio e non usa l'app
        Button nuovoCliente = new Button("Nuovo cliente", VaadinIcon.PLUS.create(), e ->
                new ClienteDialog(new DatiAnagrafica(), true, dati -> {
                    Utente u = utenti.creaCliente(dati);
                    cliente.setValue(u);
                    Avvisi.ok("Cliente creato");
                }).open());
        nuovoCliente.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Button nuovaBici = new Button("Nuova bici", VaadinIcon.PLUS.create(), e -> {
            if (cliente.getValue() == null) {
                Avvisi.errore("Scegli o crea prima il cliente");
                return;
            }
            Long clienteId = cliente.getValue().getId();
            new BiciclettaDialog(new DatiBicicletta(), true, dati -> {
                Bicicletta b = biciclette.aggiungi(clienteId, dati);
                var elenco = biciclette.elencoDi(clienteId);
                bici.setItems(elenco);
                bici.setEnabled(true);
                elenco.stream().filter(x -> x.getId().equals(b.getId())).findFirst().ifPresent(bici::setValue);
                nessunaBici.setVisible(false);
                Avvisi.ok("Bici aggiunta");
            }, null).open();
        });
        nuovaBici.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        cliente.addValueChangeListener(e -> {
            var elenco = e.getValue() == null ? java.util.List.<Bicicletta>of() : biciclette.elencoDi(e.getValue().getId());
            bici.setItems(elenco);
            bici.setEnabled(!elenco.isEmpty());
            nessunaBici.setVisible(e.getValue() != null && elenco.isEmpty());
            if (elenco.size() == 1) bici.setValue(elenco.get(0));
        });

        CheckboxGroup<TipoIntervento> interventi = new CheckboxGroup<>("Interventi");
        interventi.setItems(catalogo.attivi());
        interventi.setItemLabelGenerator(TipoIntervento::getNome);
        interventi.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        TextArea note = new TextArea("Descrizione del problema (dal cliente)");
        note.setWidthFull();

        Div rigaCliente = new Div(cliente, nuovoCliente);
        rigaCliente.addClassName("boff-riga-campo");
        Div rigaBici = new Div(bici, nuovaBici);
        rigaBici.addClassName("boff-riga-campo");
        add(rigaCliente, nessunaBici, rigaBici, interventi, note);
        Button ok = new Button("Crea lavoro", e -> {
            if (bici.getValue() == null) {
                Avvisi.errore("Scegli cliente e bici");
                return;
            }
            try {
                Lavoro l = lavori.creaAlBanco(bici.getValue().getId(),
                        interventi.getValue().stream().map(TipoIntervento::getId).toList(), note.getValue());
                close();
                creato.accept(l);
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Annulla", e -> close()), ok);
        cliente.focus();
    }
}
