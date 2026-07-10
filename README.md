# DMLS — Duper's Mod for Lazy Staff

DMLS is a client-only Fabric mod for Stoneworks staff workflows on Minecraft 1.21.11. It sends privileged commands but never grants or detects server permission.

## Install and configure

1. Install Fabric Loader, Fabric API, and this mod's JAR on the client. Mod Menu is optional.
2. Start Minecraft once to create `config/dmls.properties`. `play.stoneworks.gg` is allowed by default.
3. Add or remove proxy addresses from **Mod Options → Allowed Servers**. Exact `host[:port]` and explicit `*.host[:port]` rules are supported. The same list remains available as the comma-separated `allowedServers` property for manual administration.
4. Select a staff rank in Mod Options or with `/dmls rank`. This selection only controls which modules DMLS displays. The server remains authoritative and may reject every command.
5. Put alert words, one per line, in `config/dmls-alerts.txt`, then run `/dmls alerts reload`.

Allowlist matching is case-insensitive, normalizes the default port, supports exact hosts, and supports only explicit `*.` subdomain rules. Substring matching is not used. Privileged queues re-check the current connection before each command and cancel on disconnect or server change.

## Commands

The canonical command root is `/dmls`:

- `/dmls lands <ign...>`
- `/dmls members <land>`
- `/dmls alts <ign>`
- `/dmls xray <ign>` and `/dmls xray cancel`
- `/dmls prefix <ign> <limit> <prefixid> <prefixtext>`
- `/dmls donorpet <ign> <pet>`
- `/dmls promowave <rank> <ign...>`
- `/dmls alerts [on|off|reload]`
- `/dmls rank [rank]`
- `/dmls help`

Legacy aliases (`/checklands`, `/checkmembers`, `/dalts`, `/xray`, `/prefixlazy`, `/donorpet`, and `/promowave`) remain temporarily for compatibility. They are deprecated because generic client commands can shadow server commands and will be removed in the next breaking release. The old documentation's `/checkalts` spelling was incorrect; the implemented legacy alias is `/dalts`.

## Safety and result wording

- DMLS-local notifications bypass Fabric's incoming server-message callbacks, preventing chat-alert recursion.
- Response workflows consume only the origins they explicitly accept. Ordinary player chat is not parsed as private command output.
- Xray rollback aborts on timeout, rejection, disconnect, or server change. It never sends the next destructive rollback after an ambiguous timeout.
- “Sent” or “dispatched” means the client transmitted a command. It does **not** mean the server applied it. “Confirmed” is reserved for a recognized server response.
- Check Alts reports `unknown` when no recognized `/history` header or explicit empty response was observed. A timeout is never shown as a clean history.

The exact live Stoneworks `/history` output is not present in this repository. Its parser is intentionally isolated and narrowly anchored; anonymized examples of headers, records, empty results, failures, and end markers are needed to verify or extend it. CoreProtect matching is likewise limited to the existing `Rollback complete` response, optionally prefixed with `[CoreProtect]`. It cannot correlate that response to a username, so only the actively waiting system-response step can consume it.

## Development

Java 21 is required. Run:

```text
./gradlew clean test
./gradlew build
```

CI validates the Gradle wrapper, builds, tests, and uploads test reports on failure. The produced mod is client-only and packages `LICENSE.txt`.
