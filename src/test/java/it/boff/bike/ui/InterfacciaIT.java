package it.boff.bike.ui;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import it.boff.bike.IntegrationTest;
import it.boff.bike.bicicletta.BiciclettaService;
import it.boff.bike.bicicletta.DatiBicicletta;
import it.boff.bike.bicicletta.Tipologia;
import it.boff.bike.catalogo.CatalogoService;
import it.boff.bike.catalogo.TipoIntervento;
import it.boff.bike.lavoro.Lavoro;
import it.boff.bike.lavoro.LavoroService;
import it.boff.bike.lavoro.StatoLavoro;
import it.boff.bike.ui.componenti.SchedaBici;
import it.boff.bike.ui.views.BachecaView;
import it.boff.bike.ui.views.BiciView;
import it.boff.bike.ui.views.CalendarioView;
import it.boff.bike.ui.views.LavoriView;
import it.boff.bike.ui.views.LeMieBiciView;
import it.boff.bike.ui.views.RegistrazioneView;
import it.boff.bike.utente.UtenteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test delle schermate senza browser (girano in CI): navigazione, permessi e
 * i percorsi principali di cliente e negozio.
 */
@IntegrationTest
@ViewPackages(packages = "it.boff.bike.ui")
class InterfacciaIT extends SpringBrowserlessTest {

    @Autowired UtenteRepository utenti;
    @Autowired BiciclettaService biciclette;
    @Autowired LavoroService lavori;
    @Autowired CatalogoService catalogo;

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void bachecaDelTitolare() {
        navigate(BachecaView.class);
        assertThat($(H2.class).first().getText()).isEqualTo("Bacheca");
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteVedeLeSueBici() {
        navigate(LeMieBiciView.class);
        assertThat($(SchedaBici.class).all()).isNotEmpty();
        assertThat($(Button.class).withText("Aggiungi bici").exists()).isTrue();
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteNonApreLePagineDelNegozio() {
        navigate(LeMieBiciView.class);
        try {
            navigate(LavoriView.class);
        } catch (RuntimeException atteso) {
            // accesso negato: va bene anche un'eccezione
        }
        assertThat(getCurrentView()).isNotInstanceOf(LavoriView.class);
    }

    @Test
    void registrazioneDalTelefono() {
        navigate(RegistrazioneView.class);
        String email = "ui-" + UUID.randomUUID() + "@test.it";
        test($(TextField.class).withLabel("Nome").single()).setValue("Ugo");
        test($(TextField.class).withLabel("Cognome").single()).setValue("Interfaccia");
        test($(EmailField.class).withLabel("Email").single()).setValue(email);
        test($(TextField.class).withLabel("Telefono").single()).setValue("333 4445556");
        test($(PasswordField.class).withLabel("Password").single()).setValue("password123");
        test($(PasswordField.class).withLabel("Ripeti la password").single()).setValue("password123");
        test($(Checkbox.class).single()).click();
        test($(Button.class).withText("Crea account").single()).click();
        assertThat(utenti.findByEmail(email)).isPresent();
    }

    @Test
    @WithMockUser(username = "cliente@demo.it", roles = "CLIENTE")
    void ilClienteAnnunciaUnLavoro() {
        Long cliente = utenti.findByEmail("cliente@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Pinarello", "Dogma " + UUID.randomUUID(),
                Tipologia.STRADA)).getId();
        navigate("le-mie-bici/" + bici, BiciView.class);
        test($(Button.class).withText("Annuncia un lavoro").single()).click();

        @SuppressWarnings("unchecked")
        CheckboxGroup<TipoIntervento> scelte = $(CheckboxGroup.class).single();
        TipoIntervento primo = catalogo.attivi().get(0);
        scelte.setValue(java.util.Set.of(scelte.getListDataView().getItems()
                .filter(t -> t.getId().equals(primo.getId())).findFirst().orElseThrow()));
        test($(TextArea.class).single()).setValue("Rumore dalla ruota posteriore");
        test($(Button.class).withText("Invia").single()).click();

        List<Lavoro> suoi = lavori.storicoBiciDelCliente(cliente, bici);
        assertThat(suoi).singleElement().satisfies(l -> {
            assertThat(l.getStato()).isEqualTo(StatoLavoro.ANNUNCIATO);
            assertThat(l.getNoteCliente()).isEqualTo("Rumore dalla ruota posteriore");
        });
    }

    @Test
    @WithMockUser(username = "collaboratore@demo.it", roles = "STAFF")
    void ilNegozioFaIlCheckInDalPannello() {
        Long cliente = utenti.findByEmail("paolo.ferri@demo.it").orElseThrow().getId();
        Long bici = biciclette.aggiungi(cliente, DatiBicicletta.di("Scott", "Spark " + UUID.randomUUID(),
                Tipologia.MTB)).getId();
        Long id = lavori.annuncia(cliente, bici, List.of(catalogo.attivi().get(0).getId()), null).getId();

        navigate("lavori/" + id, LavoriView.class);
        test($(Button.class).withText("Bici arrivata in negozio").single()).click();
        assertThat(lavori.dettaglio(id).getStato()).isIn(StatoLavoro.IN_NEGOZIO, StatoLavoro.PIANIFICATO);
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void ilCalendarioMostraLePersone() {
        navigate(CalendarioView.class);
        assertThat($(H2.class).first().getText()).isEqualTo("Calendario");
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("boff-nome-persona").all())
                .extracting(d -> d.getElement().getTextRecursively())
                .contains("Mario Rossi", "Luca Bianchi");
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void ricercaRapida() {
        navigate("cerca?q=Ferri", it.boff.bike.ui.views.CercaView.class);
        String testo = getCurrentView().getElement().getTextRecursively();
        assertThat(testo).contains("Paolo Ferri", "Cannondale Trail 5");
    }

    @Test
    @WithMockUser(username = "titolare@demo.it", roles = {"STAFF", "TITOLARE"})
    void statisticheDelTitolare() {
        navigate(it.boff.bike.ui.views.StatisticheView.class);
        assertThat(getCurrentView().getElement().getTextRecursively())
                .contains("Lavori ricevuti", "Interventi più richiesti");
    }
}
