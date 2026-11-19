package it.boff.bike.notifiche;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Le iscrizioni push devono puntare solo ai servizi push dei browser (niente SSRF). */
class SicurezzaNotificheTest {

    @Test
    void ammessiSoloIServiziPushInHttps() {
        assertThat(NotificheService.endpointAmmesso("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(NotificheService.endpointAmmesso("https://updates.push.services.mozilla.com/wpush/v2/x")).isTrue();
        assertThat(NotificheService.endpointAmmesso("https://web.push.apple.com/QGp")).isTrue();
        assertThat(NotificheService.endpointAmmesso("https://wns2-par02p.notify.windows.com/w/?token=x")).isTrue();

        assertThat(NotificheService.endpointAmmesso("http://fcm.googleapis.com/fcm/send/abc")).as("non https").isFalse();
        assertThat(NotificheService.endpointAmmesso("https://localhost:8080/actuator")).isFalse();
        assertThat(NotificheService.endpointAmmesso("https://169.254.169.254/latest/meta-data")).isFalse();
        assertThat(NotificheService.endpointAmmesso("https://db:5432/")).isFalse();
        assertThat(NotificheService.endpointAmmesso("https://fcm.googleapis.com.attaccante.it/x")).isFalse();
        assertThat(NotificheService.endpointAmmesso("https://attaccante.it/fcm.googleapis.com")).isFalse();
        assertThat(NotificheService.endpointAmmesso("https://fcm.googleapis.com:8443/x")).as("porta esplicita").isFalse();
        assertThat(NotificheService.endpointAmmesso("https://utente@fcm.googleapis.com/x")).isFalse();
        assertThat(NotificheService.endpointAmmesso("non un indirizzo")).isFalse();
    }
}
