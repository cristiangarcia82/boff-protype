package it.boff.bike.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.views.AssenzeView;
import it.boff.bike.ui.views.BachecaView;
import it.boff.bike.ui.views.CalendarioView;
import it.boff.bike.ui.views.OrariView;
import it.boff.bike.ui.views.CatalogoView;
import it.boff.bike.ui.views.CercaView;
import it.boff.bike.ui.views.ClientiView;
import it.boff.bike.ui.views.LavoriView;
import it.boff.bike.ui.views.LeMieBiciView;
import it.boff.bike.ui.views.ProfiloView;
import it.boff.bike.ui.views.StatisticheView;
import it.boff.bike.ui.views.UtentiView;
import it.boff.bike.utente.Utente;
import jakarta.annotation.security.PermitAll;

/**
 * Layout principale.
 * <ul>
 *   <li>Staff: menu laterale sempre visibile sul monitor del Mac, a scomparsa sul telefono.</li>
 *   <li>Cliente: solo barra in alto, pensata per il telefono; il menu si apre dall'avatar.</li>
 * </ul>
 */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    private final UtenteCorrente utenteCorrente;

    public MainLayout(UtenteCorrente utenteCorrente) {
        this.utenteCorrente = utenteCorrente;
        if (utenteCorrente.isStaff()) {
            layoutStaff();
        } else {
            layoutCliente();
        }
    }

    private void layoutStaff() {
        setPrimarySection(Section.DRAWER);
        TextField cerca = new TextField();
        cerca.setPlaceholder("Cerca cliente, bici… (Alt+K)");
        cerca.setPrefixComponent(VaadinIcon.SEARCH.create());
        cerca.setClearButtonVisible(true);
        cerca.addClassName("boff-cerca-globale");
        cerca.setAriaLabel("Cerca");
        cerca.addFocusShortcut(com.vaadin.flow.component.Key.KEY_K, com.vaadin.flow.component.KeyModifier.ALT);
        cerca.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> getUI().ifPresent(ui ->
                ui.navigate(CercaView.class, com.vaadin.flow.router.QueryParameters.of("q", cerca.getValue()))));
        addToNavbar(new DrawerToggle(), titolo(), cerca);

        SideNav nav = new SideNav();
        for (VoceMenu voce : MenuStaff.voci(utenteCorrente.isTitolare())) {
            SideNavItem item = new SideNavItem(voce.etichetta(), voce.vista(), voce.icona().create());
            nav.addItem(item);
        }
        if (utenteCorrente.isTitolare()) {
            SideNavItem impostazioni = new SideNavItem("Impostazioni");
            impostazioni.setPrefixComponent(VaadinIcon.COG.create());
            impostazioni.addItem(new SideNavItem("Personale", UtentiView.class, VaadinIcon.USERS.create()));
            impostazioni.addItem(new SideNavItem("Catalogo interventi", CatalogoView.class, VaadinIcon.LIST.create()));
            for (VoceMenu voce : MenuStaff.impostazioni()) {
                impostazioni.addItem(new SideNavItem(voce.etichetta(), voce.vista(), voce.icona().create()));
            }
            impostazioni.setExpanded(true);
            nav.addItem(impostazioni);
        }

        VerticalLayout drawer = new VerticalLayout(nav);
        drawer.setPadding(true);
        drawer.setHeightFull();
        utenteCorrente.get().ifPresent(utente -> {
            RouterLink profilo = new RouterLink(ProfiloView.class);
            profilo.add(profilo(utente));
            profilo.addClassName("boff-link-profilo");
            VerticalLayout footer = new VerticalLayout(profilo, pulsanteEsci());
            footer.setPadding(false);
            footer.addClassNames(LumoUtility.Margin.Top.AUTO);
            drawer.add(footer);
        });
        addToDrawer(drawer);
    }

    private void layoutCliente() {
        RouterLink home = new RouterLink(LeMieBiciView.class);
        home.add(titolo());
        home.addClassName("boff-link-titolo");
        HorizontalLayout barra = new HorizontalLayout(home);
        barra.setWidthFull();
        barra.setAlignItems(FlexComponent.Alignment.CENTER);
        barra.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM);
        barra.setFlexGrow(1, home);
        utenteCorrente.get().ifPresent(utente -> {
            MenuBar menu = new MenuBar();
            menu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
            Avatar avatar = new Avatar(utente.getNomeCompleto());
            MenuItem radice = menu.addItem(avatar);
            radice.getElement().setAttribute("aria-label", "Menu utente");
            SubMenu sotto = radice.getSubMenu();
            sotto.addItem("Le mie bici", e -> naviga(LeMieBiciView.class));
            sotto.addItem("Il mio profilo", e -> naviga(ProfiloView.class));
            sotto.addItem("Installa l'app", e -> getUI().ifPresent(ui -> ui.navigate("installa")));
            sotto.addItem("Esci", e -> utenteCorrente.logout());
            barra.add(menu);
        });
        addToNavbar(barra);
    }

    private void naviga(Class<? extends Component> vista) {
        getUI().ifPresent(ui -> ui.navigate(vista));
    }

    private static H1 titolo() {
        H1 titolo = new H1("Boff Bike");
        titolo.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        return titolo;
    }

    private static Component profilo(Utente utente) {
        Avatar avatar = new Avatar(utente.getNomeCompleto());
        HorizontalLayout profilo = new HorizontalLayout(avatar, new Span(utente.getNomeCompleto()));
        profilo.setAlignItems(FlexComponent.Alignment.CENTER);
        return profilo;
    }

    private Button pulsanteEsci() {
        Button esci = new Button("Esci", VaadinIcon.SIGN_OUT.create(), e -> utenteCorrente.logout());
        esci.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return esci;
    }

    record VoceMenu(String etichetta, Class<? extends Component> vista, VaadinIcon icona) {
    }

    /** Voci del menu dello staff: aggiornate man mano che le fasi aggiungono schermate. */
    static final class MenuStaff {
        static java.util.List<VoceMenu> voci(boolean titolare) {
            return java.util.List.of(
                    new VoceMenu("Bacheca", BachecaView.class, VaadinIcon.DASHBOARD),
                    new VoceMenu("Lavori", LavoriView.class, VaadinIcon.WRENCH),
                    new VoceMenu("Calendario", CalendarioView.class, VaadinIcon.CALENDAR),
                    new VoceMenu("Clienti", ClientiView.class, VaadinIcon.USER_CARD),
                    new VoceMenu("Assenze e chiusure", AssenzeView.class, VaadinIcon.CALENDAR_USER));
        }

        static java.util.List<VoceMenu> impostazioni() {
            return java.util.List.of(new VoceMenu("Orari", OrariView.class, VaadinIcon.CLOCK),
                    new VoceMenu("Statistiche", StatisticheView.class, VaadinIcon.BAR_CHART));
        }
    }
}
