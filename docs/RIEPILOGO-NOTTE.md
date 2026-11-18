# Riepilogo della notte

## Cosa è stato fatto
| Passo | Contenuto | Commit |
|---|---|---|
| Review | 13 punti analizzati, 9 corretti (sicurezza, fuso orario, errori, lingua…) → `docs/REVIEW.md` | `Review tecnica e correzioni` |
| Fase 1 | Registrazione clienti, profilo, clienti per il negozio, personale, catalogo interventi | `Fase 1: …` |
| Fase 2 | Lavori: annuncio del cliente, check-in, stati, lavoro al banco, interventi e tempi, storico | `Fase 2: …` |
| Fase 3 | Orari vendita/officina, assenze, chiusure, festività automatiche, calendario settimanale | `Fase 3: …` |
| Fase 4 | Pianificazione automatica, interventi trascinabili, fissa/sblocca, oggi in officina, carico | `Fase 4: …` |
| Fase 5 | Notifiche push + email, password dimenticata, guida installazione, messa online con HTTPS e backup | `Fase 5: …` |

Test automatici: **50** (15 unitari + 35 di integrazione su PostgreSQL reale), tutti verdi; ogni schermata è stata
provata nel browser a misura di Mac e di iPhone.

## Come provarla (5 minuti)
```bash
BOFF_DEMO=true mvn spring-boot:run      # poi http://localhost:8080
```
1. Entra come **titolare@demo.it** / `demo1234` sul Mac: Bacheca → Calendario (trascina un intervento) → Lavori.
2. Da telefono (o finestra stretta) entra come **cliente@demo.it**: tocca la bici, guarda lo stato.
3. Torna al titolare: in *Lavori → Annunciati* fai "Bici arrivata in negozio" sulla Bianchi di Giulia e guarda
   il calendario che si aggiorna da solo.

## Da valutare tu (⚠️ in `docs/DECISIONI.md`)
1. **D3** – Clienti senza email: oggi l'email è obbligatoria (è il nome utente).
2. **D6** – Informativa privacy: è una bozza da completare.
3. **D12** – Annuncio con sola descrizione → riga "Da valutare in negozio" da 30 minuti.
4. **D18** – Festività nazionali chiuse in automatico (si può disattivare).
5. **D20** – Pianificazione semplice (primo spazio libero) invece di un ottimizzatore.

## Cosa serve da te per andare online
- Un server (VPS in Europa, ~5–10 €/mese) e un dominio/sottodominio.
- Credenziali SMTP per le email (consigliate).
- Poi si segue `docs/DEPLOY.md`.

## Limiti noti
Vedi "Limiti noti e rischi residui" in `docs/REVIEW.md`. Il più importante: le **notifiche push vanno provate
su un telefono vero** appena l'app è online (qui non era possibile raggiungere i servizi push di Apple/Google).
