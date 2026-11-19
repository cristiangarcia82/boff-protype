package it.boff.bike;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Meta;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Configurazione della "shell" HTML dell'applicazione.
 * L'annotazione {@link PWA} rende l'app installabile sulla schermata Home
 * del telefono (Android e iOS) senza passare dagli store.
 */
@PWA(name = "Boff Bike - Officina", shortName = "Boff Bike",
        description = "Gestione clienti, biciclette e lavori di officina",
        iconPath = "icons/logo.png", backgroundColor = "#262626", themeColor = "#1f6f43")
@Meta(name = "apple-mobile-web-app-capable", content = "yes")
@Meta(name = "apple-mobile-web-app-status-bar-style", content = "default")
@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@StyleSheet("styles.css")
public class AppShell implements AppShellConfigurator {
}
