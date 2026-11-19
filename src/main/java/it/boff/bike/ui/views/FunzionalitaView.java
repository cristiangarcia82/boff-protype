package it.boff.bike.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.funzionalita.Funzionalita;
import it.boff.bike.funzionalita.FunzionalitaService;
import it.boff.bike.security.Ruoli;
import it.boff.bike.ui.componenti.Avvisi;
import jakarta.annotation.security.RolesAllowed;

import java.util.function.IntConsumer;

/** Funzionalità opzionali: tutte spente finché il titolare non le attiva. */
@Route("funzionalita")
@PageTitle("Funzionalità | Boff Bike")
@RolesAllowed(Ruoli.TITOLARE)
public class FunzionalitaView extends Div {

    private final FunzionalitaService service;

    public FunzionalitaView(FunzionalitaService service) {
        this.service = service;
        addClassName("boff-pagina");

        Paragraph intro = new Paragraph("Funzioni facoltative: sono tutte spente finché non le attivi. "
                + "Puoi accenderle e spegnerle in qualsiasi momento; l'effetto è immediato.");
        intro.addClassName("boff-testo-secondario");

        IntegerField mesi = numero("Mesi dall'ultimo ritiro", service.mesiPromemoria(), 1, 36,
                service::impostaMesiPromemoria);
        IntegerField soglia = numero("Soglia di occupazione (%)", service.sogliaCarico(), 50, 100,
                service::impostaSogliaCarico);

        Div griglia = new Div(
                scheda(Funzionalita.APPROVAZIONE_EXTRA, "Approvazione dei lavori extra",
                        "Se durante il lavoro scopri che serve altro, lo proponi dal dettaglio del lavoro: il cliente "
                                + "riceve una notifica e approva o rifiuta dall'app. L'intervento entra in calendario "
                                + "solo dopo il suo sì; la bici non può essere segnata pronta finché non risponde.", null),
                scheda(Funzionalita.PROMEMORIA_MANUTENZIONE, "Promemoria di manutenzione",
                        "Ogni giorno alle 10 l'app invita al tagliando i clienti la cui bici è stata ritirata da più dei "
                                + "mesi indicati (un solo promemoria per ogni ritiro). Il cliente può disattivarli dal "
                                + "suo profilo.", mesi),
                scheda(Funzionalita.TEMPO_EFFETTIVO, "Tempo effettivo degli interventi",
                        "Quando un intervento è fatto puoi indicare quanto ci hai messo davvero. Nelle Statistiche "
                                + "compare il confronto con il tempo standard, per correggere il catalogo.", null),
                scheda(Funzionalita.RIPIANIFICAZIONE_AUTOMATICA, "Ricalcolo automatico del calendario",
                        "Accesa: dopo ogni modifica e ogni ora il calendario si riorganizza da solo (urgenze, date "
                                + "promesse, lavori non finiti spostati in avanti). Spenta: ciò che è in calendario resta "
                                + "dov'è, i lavori nuovi vanno nei primi spazi liberi e riorganizzi tu con \"Ripianifica\". "
                                + "Gli interventi fissati a mano non si spostano mai.", null),
                scheda(Funzionalita.AVVISO_CARICO, "Avviso di officina piena",
                        "Se nei prossimi 7 giorni l'officina è occupata oltre la soglia, i clienti che annunciano un "
                                + "lavoro vedono: \"i tempi di consegna potrebbero essere più lunghi del solito\". "
                                + "Non vedono numeri né orari.", soglia));
        griglia.addClassName("boff-griglia");
        add(new H2("Funzionalità"), intro, griglia);
    }

    private Component scheda(Funzionalita f, String titolo, String descrizione, Component parametro) {
        Checkbox attiva = new Checkbox("Attiva", service.attiva(f));
        Span stato = new Span();
        aggiornaStato(stato, attiva.getValue());
        attiva.addValueChangeListener(e -> {
            service.imposta(f, e.getValue());
            aggiornaStato(stato, e.getValue());
            Avvisi.ok(titolo + (e.getValue() ? ": attivata" : ": disattivata"));
        });
        Div testa = new Div(new H3(titolo), stato);
        testa.addClassName("boff-intestazione");
        Div card = new Div(testa, new Paragraph(descrizione), attiva);
        if (parametro != null) card.add(parametro);
        card.addClassNames("boff-card", "boff-colonna");
        card.getStyle().set("gap", "var(--lumo-space-s)");
        return card;
    }

    private static void aggiornaStato(Span stato, boolean attiva) {
        stato.setText(attiva ? "Attiva" : "Spenta");
        stato.getElement().getThemeList().clear();
        stato.getElement().getThemeList().add("badge");
        stato.getElement().getThemeList().add(attiva ? "success" : "contrast");
    }

    private static IntegerField numero(String etichetta, int valore, int min, int max, IntConsumer salva) {
        IntegerField f = new IntegerField(etichetta);
        f.setValue(valore);
        f.setMin(min);
        f.setMax(max);
        f.setStepButtonsVisible(true);
        f.addValueChangeListener(e -> {
            if (!e.isFromClient() || e.getValue() == null) return;
            try {
                salva.accept(e.getValue());
                Avvisi.ok("Salvato");
            } catch (ErroreDominio ex) {
                Avvisi.errore(ex.getMessage());
            }
        });
        return f;
    }
}
