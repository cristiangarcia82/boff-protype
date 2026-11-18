package it.boff.bike.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.theme.lumo.LumoUtility;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.views.BachecaView;
import it.boff.bike.ui.views.ImpostazioniView;
import it.boff.bike.utente.Utente;
import jakarta.annotation.security.PermitAll;

/**
 * Layout principale.
 * <ul>
 *   <li>Staff: menu laterale sempre visibile sul monitor del Mac, a scomparsa sul telefono.</li>
 *   <li>Cliente: solo barra in alto, pensata per il telefono.</li>
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
        addToNavbar(new DrawerToggle(), titolo());

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Bacheca", BachecaView.class, VaadinIcon.DASHBOARD.create()));
        if (utenteCorrente.isTitolare()) {
            nav.addItem(new SideNavItem("Impostazioni", ImpostazioniView.class, VaadinIcon.COG.create()));
        }

        VerticalLayout drawer = new VerticalLayout(nav);
        drawer.setPadding(true);
        drawer.setHeightFull();
        utenteCorrente.get().ifPresent(utente -> {
            VerticalLayout footer = new VerticalLayout(profilo(utente, true), pulsanteEsci());
            footer.setPadding(false);
            footer.addClassNames(LumoUtility.Margin.Top.AUTO);
            drawer.add(footer);
        });
        addToDrawer(drawer);
    }

    private void layoutCliente() {
        HorizontalLayout barra = new HorizontalLayout(titolo());
        barra.setWidthFull();
        barra.setAlignItems(FlexComponent.Alignment.CENTER);
        barra.addClassNames(LumoUtility.Padding.Horizontal.MEDIUM);
        utenteCorrente.get().ifPresent(utente -> {
            Component profilo = profilo(utente, false);
            barra.add(profilo, pulsanteEsci());
            barra.setFlexGrow(1, barra.getComponentAt(0));
        });
        addToNavbar(barra);
    }

    private static H1 titolo() {
        H1 titolo = new H1("Boff Bike");
        titolo.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        return titolo;
    }

    private static Component profilo(Utente utente, boolean conNome) {
        Avatar avatar = new Avatar(utente.getNomeCompleto());
        avatar.setTooltipEnabled(true);
        if (!conNome) {
            return avatar;
        }
        HorizontalLayout profilo = new HorizontalLayout(avatar, new Span(utente.getNomeCompleto()));
        profilo.setAlignItems(FlexComponent.Alignment.CENTER);
        return profilo;
    }

    private Button pulsanteEsci() {
        Button esci = new Button("Esci", VaadinIcon.SIGN_OUT.create(), e -> utenteCorrente.logout());
        esci.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return esci;
    }
}
