package it.boff.bike;

import it.boff.bike.bicicletta.Bicicletta;
import it.boff.bike.bicicletta.BiciclettaNonTrovataException;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.utente.Ruolo;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class BiciclettaServiceIT {

    @Autowired
    BiciclettaService service;

    @Autowired
    UtenteRepository utenti;

    Long anna;
    Long bruno;

    @BeforeEach
    void creaClienti() {
        anna = nuovoUtente(Ruolo.CLIENTE).getId();
        bruno = nuovoUtente(Ruolo.CLIENTE).getId();
    }

    private Utente nuovoUtente(Ruolo ruolo) {
        return utenti.save(new Utente(UUID.randomUUID() + "@test.it", "x", "Nome", "Cognome", ruolo, false));
    }

    @Test
    void ilClienteAggiungeEVedeSoloLeProprieBici() {
        service.aggiungi(anna, DatiBicicletta.di("Bianchi", "Impulso", Tipologia.STRADA));
        service.aggiungi(anna, DatiBicicletta.di("  Atala ", " B-Easy ", Tipologia.CITTA));
        service.aggiungi(bruno, DatiBicicletta.di("Specialized", "Stumpjumper", Tipologia.MTB));

        assertThat(service.elencoDi(anna)).extracting(Bicicletta::getNome)
                .containsExactly("Atala B-Easy", "Bianchi Impulso");
        assertThat(service.elencoDi(bruno)).extracting(Bicicletta::getNome)
                .containsExactly("Specialized Stumpjumper");
    }

    @Test
    void marcaModelloETipologiaSonoObbligatori() {
        assertThatThrownBy(() -> service.aggiungi(anna, DatiBicicletta.di(" ", "Impulso", Tipologia.STRADA)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.aggiungi(anna, DatiBicicletta.di("Bianchi", "", Tipologia.STRADA)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.aggiungi(anna, DatiBicicletta.di("Bianchi", "Impulso", null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(service.elencoDi(anna)).isEmpty();
    }

    @Test
    void unaEbikeEMarcataComeElettrica() {
        Bicicletta b = service.aggiungi(anna, DatiBicicletta.di("Haibike", "Trekking 6", Tipologia.EBIKE));
        assertThat(b.isElettrica()).isTrue();
    }

    @Test
    void unClienteNonPuoModificareORimuovereLeBiciAltrui() {
        Long biciDiAnna = service.aggiungi(anna, DatiBicicletta.di("Bianchi", "Impulso", Tipologia.STRADA)).getId();

        assertThatThrownBy(() -> service.modifica(bruno, biciDiAnna, DatiBicicletta.di("X", "Y", Tipologia.MTB)))
                .isInstanceOf(BiciclettaNonTrovataException.class);
        assertThatThrownBy(() -> service.rimuovi(bruno, biciDiAnna))
                .isInstanceOf(BiciclettaNonTrovataException.class);
        assertThat(service.elencoDi(anna)).extracting(Bicicletta::getNome).containsExactly("Bianchi Impulso");
    }

    @Test
    void modificaERimozione() {
        Long id = service.aggiungi(anna, DatiBicicletta.di("Bianchi", "Impulso", Tipologia.STRADA)).getId();

        DatiBicicletta nuovi = DatiBicicletta.di("Bianchi", "Arcadex", Tipologia.GRAVEL);
        nuovi.setTaglia("54");
        service.modifica(anna, id, nuovi);
        assertThat(service.elencoDi(anna)).singleElement().satisfies(b -> {
            assertThat(b.getNome()).isEqualTo("Bianchi Arcadex");
            assertThat(b.getTipologia()).isEqualTo(Tipologia.GRAVEL);
            assertThat(b.getTaglia()).isEqualTo("54");
        });

        service.rimuovi(anna, id);
        assertThat(service.elencoDi(anna)).isEmpty();
    }

    @Test
    void lePersoneDelloStaffNonPossiedonoBici() {
        Long staff = nuovoUtente(Ruolo.STAFF).getId();
        assertThatThrownBy(() -> service.aggiungi(staff, DatiBicicletta.di("A", "B", Tipologia.ALTRO)))
                .isInstanceOf(it.boff.bike.common.ErroreDominio.class);
    }
}
