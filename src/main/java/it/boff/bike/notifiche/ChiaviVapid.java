package it.boff.bike.notifiche;

import com.vaadin.flow.server.webpush.WebPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * Chiavi VAPID per le notifiche push. Generate al primo avvio e salvate nel database:
 * devono restare sempre le stesse, altrimenti i dispositivi iscritti smettono di ricevere.
 * Formato: base64url "grezzo" (pubblica 65 byte non compressa, privata 32 byte), quello
 * che il browser si aspetta come applicationServerKey.
 */
@Configuration
public class ChiaviVapid {

    static final String PUBBLICA = "vapid.chiave-pubblica";
    static final String PRIVATA = "vapid.chiave-privata";

    private static final Logger log = LoggerFactory.getLogger(ChiaviVapid.class);

    @Bean
    WebPush webPush(ImpostazioneRepository impostazioni, TransactionTemplate tx,
                    it.boff.bike.config.BoffProperties properties) {
        String[] chiavi = tx.execute(s -> {
            var pub = impostazioni.findById(PUBBLICA);
            var priv = impostazioni.findById(PRIVATA);
            if (pub.isPresent() && priv.isPresent()) {
                return new String[]{pub.get().getValore(), priv.get().getValore()};
            }
            String[] nuove = genera();
            impostazioni.save(new Impostazione(PUBBLICA, nuove[0]));
            impostazioni.save(new Impostazione(PRIVATA, nuove[1]));
            log.info("Generate le chiavi VAPID per le notifiche push");
            return nuove;
        });
        String soggetto = "mailto:" + properties.emailMittente();
        return new WebPush(chiavi[0], chiavi[1], soggetto);
    }

    static String[] genera() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = g.generateKeyPair();
            ECPublicKey pub = (ECPublicKey) kp.getPublic();
            ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();
            byte[] raw = new byte[65];
            raw[0] = 0x04;
            System.arraycopy(a32(pub.getW().getAffineX()), 0, raw, 1, 32);
            System.arraycopy(a32(pub.getW().getAffineY()), 0, raw, 33, 32);
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            return new String[]{b64.encodeToString(raw), b64.encodeToString(a32(priv.getS()))};
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile generare le chiavi VAPID", e);
        }
    }

    /** Intero positivo in esattamente 32 byte big-endian. */
    private static byte[] a32(BigInteger n) {
        byte[] b = n.toByteArray();
        byte[] r = new byte[32];
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, r, 32 - len, len);
        return r;
    }
}
