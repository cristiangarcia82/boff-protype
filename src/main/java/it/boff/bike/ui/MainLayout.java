package it.boff.bike.ui;

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
import jakarta.annotation.security.PermitAll;
import com.vaadin.flow.theme.lumo.LumoUtility;
import it.boff.bike.security.UtenteCorrente;
import it.boff.bike.ui.views.BachecaView;
import it.boff.bike.ui.views.ImpostazioniView;
import it.boff.bike.ui.views.LeMieBiciView;

/** Layout principale: barra in alto + menu laterale (a scomparsa su mobile). */
@Layout
@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(UtenteCorrente utenteCorrente) {
        setPrimarySection(Section.DRAWER);

        H1 titolo = new H1("Boff Bike");
        titolo.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        addToNavbar(new DrawerToggle(), titolo);

        SideNav nav = new SideNav();
        if (utenteCorrente.isStaff()) {
            nav.addItem(new SideNavItem("Bacheca", BachecaView.class, VaadinIcon.DASHBOARD.create()));
        } else {
            nav.addItem(new SideNavItem("Le mie bici", LeMieBiciView.class, VaadinIcon.WRENCH.create()));
        }
        if (utenteCorrente.isTitolare()) {
            nav.addItem(new SideNavItem("Impostazioni", ImpostazioniView.class, VaadinIcon.COG.create()));
        }

        VerticalLayout drawer = new VerticalLayout(nav);
        drawer.setPadding(true);

        utenteCorrente.get().ifPresent(utente -> {
            Avatar avatar = new Avatar(utente.getNomeCompleto());
            Span nome = new Span(utente.getNomeCompleto());
            Button esci = new Button("Esci", VaadinIcon.SIGN_OUT.create(), e -> utenteCorrente.logout());
            esci.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            HorizontalLayout profilo = new HorizontalLayout(avatar, nome);
            profilo.setAlignItems(FlexComponent.Alignment.CENTER);
            VerticalLayout footer = new VerticalLayout(profilo, esci);
            footer.setPadding(false);
            footer.addClassNames(LumoUtility.Margin.Top.AUTO);
            drawer.add(footer);
            drawer.setHeightFull();
        });

        addToDrawer(drawer);
    }
}
