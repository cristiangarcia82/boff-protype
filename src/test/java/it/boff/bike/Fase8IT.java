package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.FotoService;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.privacy.PrivacyService;
import it.boff.bike.statistiche.StatisticheService;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class Fase8IT {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};
    static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 10, 10, 10, 10};

    @Autowired FotoService foto;
    @Autowired LavoroService lavori;
    @Autowired UtenteService utenti;
    @Autowired BiciclettaService biciclette;
    @Autowired CatalogoService catalogo;
    @Autowired StatisticheService statistiche;
    @Autowired PrivacyService privacy;

    @Test
    void fotoDelClienteConLimitiEControlli() {
        Long cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        Long altro = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Look", "785", Tipologia.STRADA)).getId();
        Long lavoro = lavori.annuncia(cliente, bici, List.of(), "Crepa sul telaio?").getId();

        foto.aggiungiDelCliente(cliente, lavoro, "crepa.jpg", JPEG);
        foto.aggiungiDelCliente(cliente, lavoro, "dettaglio.png", PNG);
        assertThatThrownBy(() -> foto.aggiungiDelCliente(cliente, lavoro, "fattura.pdf", PDF))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("Formato");
        assertThatThrownBy(() -> foto.aggiungiDelCliente(altro, lavoro, "x.jpg", JPEG))
                .isInstanceOf(ErroreDominio.class);
        foto.aggiungiDelCliente(cliente, lavoro, "terza.jpg", JPEG);
        assertThatThrownBy(() -> foto.aggiungiDelCliente(cliente, lavoro, "quarta.jpg", JPEG))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("massimo");
        // il negozio può aggiungerne altre
        foto.aggiungiDelNegozio(lavoro, "officina.jpg", JPEG);

        var elenco = foto.elenco(lavoro);
        assertThat(elenco).hasSize(4);
        assertThat(elenco.get(0).tipo()).isEqualTo("image/jpeg");
        assertThat(foto.contenuto(lavoro, elenco.get(1).id())).isEqualTo(PNG);
        assertThatThrownBy(() -> foto.contenuto(lavoro + 999, elenco.get(0).id())).isInstanceOf(ErroreDominio.class);

        // eliminando l'account le foto spariscono
        lavori.annullaDelCliente(cliente, lavoro);
        privacy.eliminaAccount(cliente);
        assertThat(foto.elenco(lavoro)).isEmpty();
    }

    @Test
    void statisticheDiBase() {
        Long cliente = utenti.registraCliente(UtenteServiceIT.registrazione(UtenteServiceIT.email())).getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Giant", "TCR", Tipologia.STRADA)).getId();
        var catena = catalogo.attivi().stream().filter(t -> t.getNome().equals("Sostituzione catena")).findFirst().orElseThrow();
        Long id = lavori.creaAlBanco(bici, List.of(catena.getId()), null).getId();
        lavori.impostaDettagli(id, false, java.time.LocalDate.now().plusDays(30), null);
        lavori.segnaPronto(id);
        lavori.ritira(id);

        var r = statistiche.ultimiGiorni(30);
        assertThat(r.nuoviLavori()).isGreaterThanOrEqualTo(1);
        assertThat(r.ritirati()).isGreaterThanOrEqualTo(1);
        assertThat(r.giorniMediInNegozio()).isNotNull();
        assertThat(r.puntualitaPercento()).isNotNull();
        assertThat(r.interventiFrequenti()).extracting(StatisticheService.Intervento::descrizione)
                .contains("Sostituzione catena");
    }
}
