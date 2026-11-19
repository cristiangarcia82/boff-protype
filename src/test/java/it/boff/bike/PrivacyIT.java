package it.boff.bike;

import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.common.ErroreDominio;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.privacy.PrivacyService;
import it.boff.bike.utente.Utente;
import it.boff.bike.utente.UtenteRepository;
import it.boff.bike.utente.UtenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PrivacyIT {

    @Autowired PrivacyService privacy;
    @Autowired UtenteService utenti;
    @Autowired UtenteRepository repo;
    @Autowired BiciclettaService biciclette;
    @Autowired LavoroService lavori;
    @Autowired CatalogoService catalogo;
    @Autowired AuthenticationConfiguration auth;

    @Test
    void esportazioneEdEliminazioneDellAccount() throws Exception {
        String email = UtenteServiceIT.email();
        Utente u = utenti.registraCliente(UtenteServiceIT.registrazione(email));
        Long bici = biciclette.aggiungi(u.getId(), DatiBicicletta.di("Colnago", "C64", Tipologia.STRADA)).getId();
        Long lavoro = lavori.annuncia(u.getId(), bici, List.of(catalogo.attivi().get(0).getId()), "Telaio da controllare").getId();

        String json = privacy.esporta(u.getId());
        assertThat(json).contains("Colnago", "C64", "Telaio da controllare", "Anna", "\"biciclette\"", "\"lavori\"");
        // i tempi stimati sono un'informazione interna del negozio
        assertThat(json).doesNotContain("durata", "minuti", "Durata");

        assertThatThrownBy(() -> privacy.eliminaAccount(u.getId()))
                .isInstanceOf(ErroreDominio.class).hasMessageContaining("lavoro ancora aperto");

        lavori.annullaDelCliente(u.getId(), lavoro);
        privacy.eliminaAccount(u.getId());

        Utente anonimo = repo.findById(u.getId()).orElseThrow();
        assertThat(anonimo.isAttivo()).isFalse();
        assertThat(anonimo.getEmail()).isNull();
        assertThat(anonimo.isEliminato()).isTrue();
        assertThat(anonimo.getNomeCompleto()).isEqualTo("Cliente eliminato");
        assertThat(anonimo.getTelefono()).isNull();
        assertThat(biciclette.elencoDi(u.getId())).isEmpty();
        // lo storico del negozio resta, senza dati personali
        assertThat(lavori.lavoriDelCliente(u.getId())).singleElement()
                .satisfies(l -> assertThat(l.getNoteCliente()).isNull());
        assertThatThrownBy(() -> auth.getAuthenticationManager()
                .authenticate(new UsernamePasswordAuthenticationToken(email, "password1")))
                .isNotInstanceOf(DisabledException.class); // l'email originale non esiste più
    }

    @Test
    void ilPersonaleNonUsaQuesteFunzioni() {
        Utente staff = utenti.creaCollaboratore(UtenteServiceIT.registrazione(UtenteServiceIT.email()));
        assertThatThrownBy(() -> privacy.eliminaAccount(staff.getId())).isInstanceOf(ErroreDominio.class);
    }
}
