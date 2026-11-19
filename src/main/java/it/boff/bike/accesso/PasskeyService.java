package it.boff.bike.accesso;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Le passkey (Face ID / Touch ID / impronta) di ciascun utente. La registrazione e l'accesso
 * passano dagli endpoint WebAuthn di Spring Security; qui solo elenco e rimozione.
 */
@Service
@Transactional
public class PasskeyService {

    public record Passkey(String id, String nome, Instant creata, Instant ultimoUso) {
    }

    private final PublicKeyCredentialUserEntityRepository utentiWebAuthn;
    private final UserCredentialRepository credenziali;

    public PasskeyService(PublicKeyCredentialUserEntityRepository utentiWebAuthn, UserCredentialRepository credenziali) {
        this.utentiWebAuthn = utentiWebAuthn;
        this.credenziali = credenziali;
    }

    @Transactional(readOnly = true)
    public List<Passkey> elenco(String email) {
        PublicKeyCredentialUserEntity u = email == null ? null : utentiWebAuthn.findByUsername(email);
        if (u == null) return List.of();
        return credenziali.findByUserId(u.getId()).stream()
                .map(c -> new Passkey(c.getCredentialId().toBase64UrlString(), c.getLabel(), c.getCreated(), c.getLastUsed()))
                .sorted(Comparator.comparing(Passkey::creata, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Rimuove una passkey, solo se appartiene all'utente indicato. */
    public void rimuovi(String email, String passkeyId) {
        PublicKeyCredentialUserEntity u = email == null ? null : utentiWebAuthn.findByUsername(email);
        if (u == null) return;
        CredentialRecord c = credenziali.findByCredentialId(Bytes.fromBase64(passkeyId));
        if (c != null && c.getUserEntityUserId().equals(u.getId())) {
            credenziali.delete(c.getCredentialId());
        }
    }

    /** Tutte le passkey e l'identità WebAuthn dell'utente (cancellazione dei dati personali). */
    public void eliminaTutte(String email) {
        PublicKeyCredentialUserEntity u = email == null ? null : utentiWebAuthn.findByUsername(email);
        if (u == null) return;
        credenziali.findByUserId(u.getId()).forEach(c -> credenziali.delete(c.getCredentialId()));
        utentiWebAuthn.delete(u.getId());
    }
}
