# Implementation plan — syllabus integration

`docs/cse1007-coverage-plan.md` is the earlier sketch. The running code is the simpler version described here. A name appears here only if some live path calls it.

The seven course modules below are wired into the demo. Module 8 is the short note in `docs/contemporary-issues.md`.

## Decisions

- Spring Boot remains the host. Servlets and JSP run inside the same process as the REST API and JavaFX, on port 8080.
- JavaFX stays the desktop UI. The JSP page is the browser UI. Both write through `JdbcLedger`. JavaFX reads `/api/accounts` and `/api/transactions` (JPA). The JSP reads `JdbcLedger.listAccounts()` and `JdbcLedger.listRecentTransactions()`. Both use the same H2 file.
- Money movement uses JDBC (`Connection`, `PreparedStatement`, `commit`, `rollback`). JPA still creates the tables and still serves `/api/accounts` and `/api/transactions`, so the existing JavaFX client does not change.
- The H2 database file is `data/upimesh`. Balances and the ledger survive a restart. Tests keep using an in-memory database so they do not touch that file.
- Mesh packets are also written to `data/mesh/packets.ser` with `ObjectOutputStream`. `MeshPacketFileStore.save` throws the checked exception `MeshException`. Each settlement is appended to `data/ledger/settlements.log`.
- The running database is the H2 file. `application-mysql.properties` is an optional profile: `mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` after a local database named `upimesh` exists. Tests do not use it.
- `Payer.sign()` hashes the PIN into `PaymentInstruction.pinHash`, and that field is encrypted with the rest of the instruction. No method calls `getPinHash()`. The ledger table has no PIN column.

## What each module uses

### Module 1 — Java fundamentals

Conditional checks and loops drive gossip and ingestion. An examiner can also open these call sites:

- `Vpa` splits `name@demo`. `Payer` keeps the four demo people in three parallel arrays, filled in a static block, and `ofVpa` walks those arrays.
- `MeshTopology` is a `boolean[][]`. Gossip uses nested loops over that table.
- `Payer.sign()` rejects a bad address, a non-positive amount, a blank PIN, and a payment to yourself.
- `LedgerServlet.processUploads()` is the Flush action. It loops over packets held by phones with internet. The Settle action does not call it.

### Module 2 — Object-oriented programming

| Class | Role |
|---|---|
| `Payer` | `DemoService.createPacket()` calls `Payer.ofVpa(sender).sign(...)`. The one-argument constructor calls `this(...)`. `getDeviceId()` maps each demo person to a phone. The demo always injects at `phone-alice`. |
| `Vpa` | `Payer.sign()` parses the receiver with `new Vpa(...)`. |
| `MeshDevice` | Abstract phone. `OfflineDevice` and `BridgeDevice` call `super(id)`. `@MeshNode` says which one has internet, and `hasInternet()` reads that annotation. |
| `PacketCarrier` | The interface `MeshDevice` implements. |
| `MeshPacket` | Wire format, `Serializable`. The inner class `Hop` builds the forwarded copy (`nextHop()`). |
| `PaymentInstruction` | Decrypted payload. Constructed by `Payer`, encrypted by `DemoService`. |

`DemoService.createPacket()` goes through `Payer` before encryption.

### Module 3 — Robustness and concurrency

`IdempotencyConcurrencyTest` still fires three threads at one packet and expects one settlement.

`MeshSimulatorService.gossipOnce()` is the mesh's own threads. It starts one `Thread` per phone (`PhoneForwarder`). Each thread only calls `PacketInbox.add`. The caller then `take()`s each copy and stores it. `add` / `take` use `wait` and `notifyAll` on the inbox. The phone threads never lock a phone, so two phones cannot deadlock. `JdbcLedger.transfer()` updates the two accounts in VPA order for the same reason, inside `try` / `catch` / `finally` (rollback and `close` are in `finally`).

Three behaviours make the three-bridge test hold:

- The idempotency key is the SHA-256 of the **decoded ciphertext bytes**. Java's Base64 decoder ignores spare bits in the last character, so two different Base64 strings can be the same packet. Hashing the text would settle that packet twice.
- `claim()` is released when decryption fails, the timestamp is missing, the account is unknown (`UnknownAccountException`), or settlement throws before commit (`LedgerConflictException` after three tries, or any other exception). A stale or future-dated packet throws `StalePacketException` and keeps the claim, as does a committed `SETTLED` or `REJECTED` row. `DuplicatePacketException` also keeps the claim, because the row is already there.
- `SettlementService` retries a conflicting update up to three times. Two different payments that both fit in the balance can both commit. If the second no longer fits, it is stored as `REJECTED`.

`LedgerFlowTest` covers insufficient funds, an unknown receiver (claim released, so a retry is not reported as a duplicate), and a non-canonical Base64 replay.

### Module 4 — Files, streams, and serialization

| Piece | Where |
|---|---|
| H2 file database | `jdbc:h2:file:./data/upimesh` in `application.properties`. `ddl-auto=update`. |
| JDBC reads and writes | `JdbcLedger` |
| Object stream | `MeshPacketFileStore` saves and restores each device's packets |
| Character stream | `JdbcLedger` appends `data/ledger/settlements.log` after commit |
| `List`, `Set`, `Map` | a phone's packet list and seen-id set; the idempotency map |
| One lambda | `MeshSimulatorService.bridges()` filters phones with `hasInternet()` |
| Generics | `PacketInbox<T>` |

Delete the `data/` folder and restart to wipe accounts, the ledger, mesh packets, and the log. Accounts are seeded again only when the table is empty.

### Module 5 — GUI and database connectivity

JavaFX (`UpiMeshFxApplication`) shows devices, balances, the ledger, and the log. Inject drops the packet on `phone-alice`. Gossip and Flush are the next two steps, on the buttons and on the Mesh menu.

Debit and credit happen in `JdbcLedger.transfer()`:

1. Open a connection and `setAutoCommit(false)`
2. Update the two balances in sorted VPA order, each with a version check
3. Insert the ledger row
4. `commit()`
5. `finally` rolls back when commit did not happen, then closes the connection
6. After that, append `data/ledger/settlements.log`

An insufficient balance writes a `REJECTED` row and does not change balances. That outcome is returned as `REJECTED`, not `SETTLED`.

### Module 6 — Servlets

| Servlet | Methods | Path |
|---|---|---|
| `LedgerServlet` | `init` and `destroy` log one line. `doGet` renders the page from JDBC and counts visits on the `HttpSession`. `doPost` actions: `settle`, `gossip`, `flush` (`processUploads`), `reset`. | `/ledger` |
| `BridgeIngestServlet` | `init` and `destroy` log one line. `doGet` describes the endpoint. `doPost` accepts a `MeshPacket` JSON body, same pipeline as `/api/bridge/ingest`. | `/servlet/ingest` |

`ServletConfig` registers both servlets and sets the session timeout to 30 minutes. `src/main/webapp/WEB-INF/web.xml` records the same timeout and the same URL patterns. Embedded Tomcat does not load that file; the Java registration is the one that runs.

`/` redirects to `/ledger`.

### Module 7 — JSP

`src/main/webapp/WEB-INF/jsp/ledger.jsp` uses JSTL `forEach` and Expression Language: `${account.vpa}`, `${account.holderName}`, `${account.balance}`, `${tx.amount}`, `${tx.status}`, `${tx.bridgeNodeId}`, `${device.deviceId}`, `${device.online}`, `${device.packetCount}`, `${cacheSize}`, `${message}`. `LedgerServlet.doGet` sets every one of those attributes.

Amounts go through the custom tag `<mesh:rupee>`, defined by `RupeeTag` and `WEB-INF/mesh.tld`. The visit count is a `LedgerPage` bean: the servlet puts `pageBean` on the request, and the page reads it with `jsp:useBean` and `jsp:getProperty`.

## How a payment moves

1. JavaFX `POST /api/demo/send` and the JSP Settle button both call `DemoService.createPacket()`.
2. `Payer.sign()` fills the instruction. `HybridCryptoService.encrypt()` seals it. The PIN hash is inside that ciphertext and is not read again.
3. Both screens drop the packet on `phone-alice` and stop there. Gossip copies it to the other phones, including `phone-bridge`. Flush uploads only what that bridge phone is holding, and that upload is what settles the payment.
4. `inject` and `gossipOnce` call `MeshPacketFileStore.save`. Startup calls `restore`.
5. `ingest` hashes the decoded ciphertext, claims that hash, decrypts, checks freshness, then `SettlementService.settle()`, which calls `JdbcLedger.transfer()` up to three times on a version clash.
6. `transfer` commits the debit, the credit, and the ledger row together. `finally` rolls back and closes when commit did not happen. The log is appended after the connection is closed.
7. The JSP reloads balances through JDBC. JavaFX reloads them through the JPA API. Same file, two readers.

## How to run

From the project folder, with JDK 17 or newer:

```cmd
mvnw.cmd test
mvnw.cmd spring-boot:run
```

Then open http://localhost:8080/ledger.

The desktop window is still:

```cmd
mvnw.cmd javafx:run
```

Do not start both at once; they share port 8080.

H2 console: http://localhost:8080/h2-console  
JDBC URL `jdbc:h2:file:./data/upimesh`, user `sa`, blank password.

## Left as demo limits

- No real UPI, bank, or PIN check.
- No authentication on the HTTP endpoints.
- The mesh is in-process, not Bluetooth.
- JSP works with `mvnw spring-boot:run` from the project directory (Tomcat is pointed at `src/main/webapp`). A packaged executable jar will not serve JSP pages.
