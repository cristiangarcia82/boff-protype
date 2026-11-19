package it.boff.bike.ui.views;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import it.boff.bike.config.BoffProperties;

/**
 * Informativa privacy (art. 13 GDPR). ATTENZIONE: testo di BOZZA, da far verificare e completare
 * (ragione sociale, indirizzo, P. IVA, contatti, fornitori effettivi) prima della messa online.
 * Valida sia per chi usa l'app sia per i clienti registrati al banco (che la ricevono in negozio).
 */
@Route(value = "privacy", autoLayout = false)
@PageTitle("Informativa privacy | Boff Bike")
@AnonymousAllowed
public class PrivacyView extends Div {

    static final String VERSIONE = "versione 3 · novembre 2026";

    public PrivacyView(BoffProperties properties) {
        String negozio = properties.nomeNegozio();
        addClassNames("boff-pagina", "boff-pagina-cliente");
        Paragraph bozza = new Paragraph("Bozza da completare a cura del negozio prima della pubblicazione "
                + "(dati del titolare e fornitori effettivi).");
        bozza.addClassName("boff-avviso");
        Paragraph versione = new Paragraph(VERSIONE);
        versione.addClassName("boff-testo-secondario");

        add(new H2("Informativa privacy"), versione, bozza,

                new H3("Chi tratta i tuoi dati"),
                new Paragraph("Il titolare del trattamento è " + negozio
                        + " [ragione sociale, indirizzo, P. IVA, email e telefono del negozio]."),

                new H3("Quali dati raccogliamo"),
                elenco("Dati di contatto: nome, cognome, telefono e, se usi l'app, email.",
                        "Le tue biciclette: marca, modello, tipologia ed eventuali dettagli (taglia, colore, "
                                + "anno, numero di telaio, note).",
                        "I lavori richiesti e svolti, con le tue note e le foto che scegli di caricare.",
                        "Se usi l'app: password (conservata solo in forma cifrata) e, se le attivi, "
                                + "le iscrizioni alle notifiche sui tuoi dispositivi.",
                        "Se entri con Face ID, Touch ID o l'impronta: una chiave pubblica e il nome del dispositivo "
                                + "(es. \"iPhone\"). Il viso e l'impronta restano sul tuo dispositivo: non li riceviamo mai.",
                        "Se entri con un codice via email: il codice, conservato solo in forma cifrata, "
                                + "che vale 10 minuti."),
                new Paragraph("Se non usi l'app, ti registriamo noi in negozio con i soli dati necessari "
                        + "(nome, cognome e telefono) e ti avvisiamo per telefono."),

                new H3("Perché e su quale base"),
                elenco("Gestire l'assistenza alle tue biciclette, organizzare il lavoro dell'officina e "
                                + "avvisarti quando la bici è pronta o serve una tua risposta su un intervento in più: "
                                + "esecuzione del servizio che ci chiedi.",
                        "Promemoria per il tagliando, se il negozio li attiva: legittimo interesse a "
                                + "ricordarti la manutenzione. Puoi disattivarli in ogni momento dal profilo; "
                                + "senza app non ti arrivano.",
                        "Obblighi di legge, quando previsti (per esempio fiscali)."),
                new Paragraph("I dati non sono usati per pubblicità, non sono ceduti a terzi e non vengono "
                        + "usati per decisioni automatiche che ti riguardano."),

                new H3("Chi può vederli"),
                new Paragraph("Solo chi lavora in negozio. Per far funzionare il servizio ci appoggiamo a "
                        + "fornitori che agiscono per nostro conto: il server che ospita l'app in Europa "
                        + "[nome del fornitore], il servizio di invio email [nome del fornitore] e, per le "
                        + "notifiche, il servizio del produttore del tuo telefono o browser (Apple, Google, "
                        + "Mozilla, Microsoft), che riceve solo il messaggio da mostrarti."),

                new H3("Per quanto tempo"),
                new Paragraph("Finché sei nostro cliente. Se chiedi la cancellazione, nome, contatti, bici, "
                        + "note e foto vengono eliminati; lo storico dei lavori resta solo in forma anonima, "
                        + "senza nulla che ti renda riconoscibile. I documenti fiscali, se presenti, si "
                        + "conservano per il tempo previsto dalla legge."),

                new H3("I tuoi diritti"),
                new Paragraph("Puoi chiedere di vedere, correggere, cancellare o limitare i tuoi dati, "
                        + "riceverne una copia, e opporti al loro uso per i promemoria."),
                elenco("Con l'app: dal tuo profilo puoi scaricare una copia dei dati o eliminare l'account.",
                        "Senza app: chiedilo in negozio, per telefono o per email. Ti consegniamo la copia dei "
                                + "dati o li cancelliamo noi, di solito subito e comunque entro 30 giorni."),
                new Paragraph("Se ritieni che i tuoi dati siano trattati in modo scorretto puoi rivolgerti al "
                        + "Garante per la protezione dei dati personali (www.garanteprivacy.it)."));
    }

    private static UnorderedList elenco(String... voci) {
        UnorderedList ul = new UnorderedList();
        for (String v : voci) {
            ul.add(new ListItem(v));
        }
        return ul;
    }
}
