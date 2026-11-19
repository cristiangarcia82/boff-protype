package it.boff.bike.ui.componenti;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.function.Consumer;

/**
 * Passkey (Face ID, Touch ID, impronta) dal browser: parla con gli endpoint WebAuthn di Spring Security
 * ({@code /webauthn/register/options}, {@code /webauthn/register}, {@code /webauthn/authenticate/options},
 * {@code /login/webauthn}). La chiave privata resta sul dispositivo: al server arriva solo quella pubblica.
 */
public final class Passkey {

    private Passkey() {
    }

    /** Funzioni comuni: base64url e POST JSON con il token CSRF. */
    private static final String BASE = """
            const b64 = {
              enc: (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/=/g, '').replace(/\\+/g, '-').replace(/\\//g, '_'),
              dec: (s) => { const b = atob(s.replace(/-/g, '+').replace(/_/g, '/')); const a = new Uint8Array(b.length);
                            for (let i = 0; i < b.length; i++) a[i] = b.charCodeAt(i); return a.buffer; }
            };
            const post = async (url, body) => {
              const h = { 'Content-Type': 'application/json' };
              if ($0) h[$0] = $1;
              const r = await fetch(url, { method: 'POST', headers: h, credentials: 'same-origin',
                                           body: body ? JSON.stringify(body) : undefined });
              if (!r.ok) throw new Error('HTTP ' + r.status);
              return r.json();
            };
            """;

    private static final String SUPPORTATA = """
            return !!(window.PublicKeyCredential && PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable
                      && await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable());
            """;

    private static final String REGISTRA = BASE + """
            try {
              const o = await post('webauthn/register/options');
              const opzioni = { ...o, challenge: b64.dec(o.challenge), user: { ...o.user, id: b64.dec(o.user.id) },
                                excludeCredentials: (o.excludeCredentials || []).map(c => ({ ...c, id: b64.dec(c.id) })) };
              const c = await navigator.credentials.create({ publicKey: opzioni });
              const ua = navigator.userAgent;
              const nome = /iPhone/.test(ua) ? 'iPhone' : /iPad/.test(ua) ? 'iPad' : /Android/.test(ua) ? 'Android'
                         : /Macintosh/.test(ua) ? 'Mac' : /Windows/.test(ua) ? 'PC Windows' : 'Questo dispositivo';
              const esito = await post('webauthn/register', { publicKey: { label: nome, credential: {
                id: c.id, rawId: b64.enc(c.rawId), type: c.type,
                response: { attestationObject: b64.enc(c.response.attestationObject),
                            clientDataJSON: b64.enc(c.response.clientDataJSON),
                            transports: c.response.getTransports ? c.response.getTransports() : [] },
                clientExtensionResults: c.getClientExtensionResults(), authenticatorAttachment: c.authenticatorAttachment } } });
              return esito && esito.success ? 'ok' : 'errore';
            } catch (e) {
              console.warn(e);
              return e && e.name === 'NotAllowedError' ? 'annullato' : e && e.name === 'InvalidStateError' ? 'gia' : 'errore';
            }
            """;

    private static final String ACCEDI = BASE + """
            try {
              const o = await post('webauthn/authenticate/options');
              const opzioni = { ...o, challenge: b64.dec(o.challenge),
                                allowCredentials: (o.allowCredentials || []).map(c => ({ ...c, id: b64.dec(c.id) })) };
              const c = await navigator.credentials.get({ publicKey: opzioni });
              const r = c.response;
              const esito = await post('login/webauthn', {
                id: c.id, rawId: b64.enc(c.rawId), credType: c.type,
                response: { authenticatorData: b64.enc(r.authenticatorData), clientDataJSON: b64.enc(r.clientDataJSON),
                            signature: b64.enc(r.signature), userHandle: r.userHandle ? b64.enc(r.userHandle) : undefined },
                clientExtensionResults: c.getClientExtensionResults(), authenticatorAttachment: c.authenticatorAttachment });
              if (esito && esito.authenticated && esito.redirectUrl) { window.location.href = esito.redirectUrl; return 'ok'; }
              return 'errore';
            } catch (e) {
              console.warn(e);
              return e && e.name === 'NotAllowedError' ? 'annullato' : 'errore';
            }
            """;

    /** executeJs esegue il corpo di una funzione normale: per usare await serve una funzione async. */
    private static String async(String corpo) {
        return "return (async () => {\n" + corpo + "\n})();";
    }

    /** true se il dispositivo ha Face ID, Touch ID, Windows Hello o impronta utilizzabili dal browser. */
    public static void supportata(UI ui, Consumer<Boolean> risposta) {
        ui.getPage().executeJs(async(SUPPORTATA)).then(Boolean.class, risposta::accept, errore -> risposta.accept(false));
    }

    /** Crea una passkey per l'utente collegato. Esito: "ok", "annullato", "gia" (già presente), "errore". */
    public static void registra(UI ui, Consumer<String> esito) {
        String[] csrf = csrf();
        ui.getPage().executeJs(async(REGISTRA), csrf[0], csrf[1]).then(String.class, esito::accept, e -> esito.accept("errore"));
    }

    /** Accede con una passkey; se riesce, la pagina si ricarica. Esito: "ok", "annullato", "errore". */
    public static void accedi(UI ui, Consumer<String> esito) {
        String[] csrf = csrf();
        ui.getPage().executeJs(async(ACCEDI), csrf[0], csrf[1]).then(String.class, esito::accept, e -> esito.accept("errore"));
    }

    /** Intestazione e valore del token CSRF di Spring Security per le chiamate fetch. */
    public static String[] csrf() {
        VaadinServletRequest req = VaadinServletRequest.getCurrent() instanceof VaadinServletRequest r ? r : null;
        Object t = req == null ? null : req.getHttpServletRequest().getAttribute(CsrfToken.class.getName());
        if (t instanceof CsrfToken token) {
            return new String[] {token.getHeaderName(), token.getToken()};
        }
        return new String[] {"", ""};
    }

    /** Nome del parametro e valore del token CSRF per un modulo HTML classico. */
    public static String[] csrfModulo() {
        VaadinServletRequest req = VaadinServletRequest.getCurrent() instanceof VaadinServletRequest r ? r : null;
        Object t = req == null ? null : req.getHttpServletRequest().getAttribute(CsrfToken.class.getName());
        if (t instanceof CsrfToken token) {
            return new String[] {token.getParameterName(), token.getToken()};
        }
        return new String[] {"", ""};
    }
}
