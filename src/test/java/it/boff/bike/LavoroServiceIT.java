package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaNonTrovataException;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroCambiato;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.RigaLavoro;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@RecordApplicationEvents
class LavoroServiceIT {

    @Autowired LavoroService service;
    @Autowired UtenteService utenti;
    @Autowired BiciclettaService biciclette;
    @Autowired CatalogoService catalogo;
    @Autowired ApplicationEvents eventi;

    Long cliente;
    Long altroCliente;
    Long bici;
    TipoIntervento tagliando;
    TipoIntervento catena;

    @BeforeEach
    void prepara() {
        cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        altroCliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Bianchi", "Impulso", Tipologia.STRADA)).getId();
        List<TipoIntervento> attivi = catalogo.attivi();
        tagliando = attivi.get(0);
        catena = attivi.stream().filter(t -> t.getNome().equals("Sostituzione catena")).findFirst().orElseThrow();
    }

    @Test
    void cicloCompletoDalLAnnuncioAlRitiro() {
        Lavoro l = service.annuncia(cliente, bici, List.of(tagliando.getId(), catena.getId()), "Cigola il movimento centrale");
        assertThat(l.getStato()).isEqualTo(StatoLavoro.ANNUNCIATO);
        Long id = l.getId();
        Lavoro d = service.dettaglio(id);
        assertThat(d.getRighe()).hasSize(2);
        assertThat(d.minutiTotali()).isEqualTo(tagliando.getDurataStandardMin() + catena.getDurataStandardMin());
        assertThat(d.getNoteCliente()).isEqualTo("Cigola il movimento centrale");

        service.checkIn(id);
        assertThat(service.dettaglio(id).getStato()).isEqualTo(StatoLavoro.IN_NEGOZIO);
        assertThat(service.dettaglio(id).getCheckinIl()).isNotNull();

        RigaLavoro prima = service.dettaglio(id).getRighe().get(0);
        service.segnaRiga(prima.getId(), true);
        assertThat(service.dettaglio(id).getStato()).isEqualTo(StatoLavoro.IN_LAVORAZIONE);
        assertThat(service.dettaglio(id).minutiDaFare()).isEqualTo(catena.getDurataStandardMin());

        service.segnaPronto(id);
        Lavoro pronto = service.dettaglio(id);
        assertThat(pronto.getStato()).isEqualTo(StatoLavoro.PRONTO);
        assertThat(pronto.getRighe()).allMatch(RigaLavoro::isCompletata);

        service.ritira(id);
        assertThat(service.dettaglio(id).getStato()).isEqualTo(StatoLavoro.RITIRATO);
        assertThat(service.storicoBiciDelCliente(cliente, bici)).extracting(Lavoro::getId).containsExactly(id);

        assertThat(eventi.stream(LavoroCambiato.class).filter(e -> e.lavoroId().equals(id)).map(LavoroCambiato::dopo))
                .containsExactly(StatoLavoro.ANNUNCIATO, StatoLavoro.IN_NEGOZIO, StatoLavoro.IN_LAVORAZIONE,
                        StatoLavoro.PRONTO, StatoLavoro.RITIRATO);
    }

    @Test
    void transizioniNonAmmesseVengonoRifiutate() {
        Long id = service.annuncia(cliente, bici, List.of(tagliando.getId()), null).getId();
        assertThatThrownBy(() -> service.segnaPronto(id)).isInstanceOf(ErroreDominio.class)
                .hasMessageContaining("Annunciato");
        assertThatThrownBy(() -> service.ritira(id)).isInstanceOf(ErroreDominio.class);
        service.annulla(id);
        assertThatThrownBy(() -> service.aggiungiLibera(id, "Altro", 10)).isInstanceOf(ErroreDominio.class)
                .hasMessageContaining("chiuso");
    }

    @Test
    void unaBiciHaUnSoloLavoroAperto() {
        service.annuncia(cliente, bici, List.of(tagliando.getId()), null);
        assertThatThrownBy(() -> service.annuncia(cliente, bici, List.of(catena.getId()), null))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("già un lavoro aperto");
    }

    @Test
    void serveAlmenoUnInterventoOUnaDescrizione() {
        assertThatThrownBy(() -> service.annuncia(cliente, bici, List.of(), "  "))
                .isInstanceOf(ErroreDominio.class);
        Lavoro soloNote = service.annuncia(cliente, bici, List.of(), "Fa un rumore strano");
        assertThat(service.dettaglio(soloNote.getId()).getRighe()).singleElement()
                .satisfies(r -> assertThat(r.getDescrizione()).isEqualTo("Da valutare in negozio"));
    }

    @Test
    void unClienteNonPuoAnnunciareOAnnullareSuBiciAltrui() {
        assertThatThrownBy(() -> service.annuncia(altroCliente, bici, List.of(tagliando.getId()), null))
                .isInstanceOf(BiciclettaNonTrovataException.class);
        Long id = service.annuncia(cliente, bici, List.of(tagliando.getId()), null).getId();
        assertThatThrownBy(() -> service.annullaDelCliente(altroCliente, id)).isInstanceOf(ErroreDominio.class);
        assertThat(service.lavoriDelCliente(altroCliente)).isEmpty();
    }

    @Test
    void ilClienteAnnullaSoloPrimaDelCheckIn() {
        Long id = service.annuncia(cliente, bici, List.of(tagliando.getId()), null).getId();
        service.checkIn(id);
        assertThatThrownBy(() -> service.annullaDelCliente(cliente, id))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("già in negozio");
    }

    @Test
    void lavoroAlBancoERigheModificabili() {
        Lavoro l = service.creaAlBanco(bici, List.of(), "Controllo generale");
        assertThat(l.getStato()).isEqualTo(StatoLavoro.IN_NEGOZIO);
        RigaLavoro extra = service.aggiungiLibera(l.getId(), "Sostituzione nastro manubrio", 25);
        service.modificaDurata(extra.getId(), 35);
        service.aggiungiDaCatalogo(l.getId(), catena.getId());
        assertThat(service.dettaglio(l.getId()).getRighe()).hasSize(3);
        assertThatThrownBy(() -> service.modificaDurata(extra.getId(), 2)).isInstanceOf(ErroreDominio.class);
        service.rimuoviRiga(extra.getId());
        assertThat(service.dettaglio(l.getId()).getRighe()).hasSize(2);
        // fuori transazione: le righe devono essere già caricate (l'interfaccia mostra i minuti)
        assertThat(service.cerca(StatoLavoro.IN_OFFICINA, "bianchi impulso"))
                .filteredOn(x -> x.getId().equals(l.getId())).singleElement()
                .satisfies(x -> assertThat(x.minutiDaFare()).isEqualTo(30 + catena.getDurataStandardMin()));
    }
}
