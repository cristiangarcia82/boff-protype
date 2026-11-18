package it.boff.bike.notifiche;

import com.sun.net.httpserver.HttpServer;
import com.vaadin.flow.server.webpush.WebPush;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushMessage;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica che le chiavi generate siano nel formato giusto: una notifica viene cifrata,
 * firmata (VAPID) e inviata a un finto servizio push locale.
 */
class ChiaviVapidTest {

    @Test
    void chiaviNelFormatoAttesoDalBrowser() {
        String[] k = ChiaviVapid.genera();
        Base64.Decoder d = Base64.getUrlDecoder();
        assertThat(d.decode(k[0])).hasSize(65).startsWith((byte) 0x04);
        assertThat(d.decode(k[1])).hasSize(32);
    }

    @Test
    void unaNotificaVieneCifrataFirmataEInviata() throws Exception {
        AtomicReference<String> autorizzazione = new AtomicReference<>();
        AtomicReference<String> codifica = new AtomicReference<>();
        AtomicReference<Integer> lunghezza = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push", ex -> {
            autorizzazione.set(ex.getRequestHeaders().getFirst("Authorization"));
            codifica.set(ex.getRequestHeaders().getFirst("Content-Encoding"));
            lunghezza.set(ex.getRequestBody().readAllBytes().length);
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        server.start();
        try {
            String[] k = ChiaviVapid.genera();
            WebPush push = new WebPush(k[0], k[1], "mailto:test@example.com");
            push.sendNotification(new WebPushSubscription(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/push", chiaviBrowser()),
                    new WebPushMessage("Bici pronta", "Passa a ritirarla"));
            assertThat(autorizzazione.get()).startsWith("vapid t=");
            assertThat(codifica.get()).isEqualTo("aes128gcm");
            assertThat(lunghezza.get()).isGreaterThan(50);
        } finally {
            server.stop(0);
        }
    }

    /** Chiavi di un finto browser iscritto (p256dh = chiave pubblica P-256, auth = 16 byte casuali). */
    private static WebPushKeys chiaviBrowser() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey pub = (ECPublicKey) g.generateKeyPair().getPublic();
        byte[] raw = new byte[65];
        raw[0] = 4;
        System.arraycopy(a32(pub.getW().getAffineX()), 0, raw, 1, 32);
        System.arraycopy(a32(pub.getW().getAffineY()), 0, raw, 33, 32);
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        return new WebPushKeys(e.encodeToString(raw), e.encodeToString(auth));
    }

    private static byte[] a32(BigInteger n) {
        byte[] b = n.toByteArray(), r = new byte[32];
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, r, 32 - len, len);
        return r;
    }
}
