# CSE1007 coverage plan

Source: `docs/CSE1007_JAVA-PROGRAMMING_ETH_1.0_52_CSE1007_64 ACP.pdf` (CSE1007 Java Programming, syllabus version 1.0, Academic Council No. 52).

The running code is the simpler design in `docs/implementation-plan.md`: one thread per phone, a single `wait`/`notify` inbox, plain loops. This file's heavier sketch (a queue on every phone) was not built.

`docs/implementation-plan.md` records what the live paths call.

## Already true, so later work should extend it

| Syllabus item | Already visible in |
|---|---|
| Conditionals, loops, try-catch | `Payer.sign()`, `LedgerServlet` flush loop, `MeshPacketFileStore.restore()` |
| Classes, constructors, methods | `Payer`, `MeshPacket`, `PaymentInstruction` |
| Packages | `model`, `service`, `jdbc`, `io`, `web`, `fx`, `crypto` |
| User-defined exceptions | `LedgerConflictException`, `DuplicatePacketException` |
| A concurrent test | `IdempotencyConcurrencyTest` (three threads, one settlement) |
| `synchronized` methods | `MeshSimulatorService`, `MeshPacketFileStore` |
| Files, byte streams, serialization | `data/mesh/packets.ser` via `ObjectOutputStream` |
| Character stream | `data/ledger/settlements.log` |
| `List` and `Map` | device packet lists, the idempotency map |
| JDBC transaction | `JdbcLedger.transfer()` (`setAutoCommit(false)`, `commit`, `rollback`) |
| JavaFX controls and events | `UpiMeshFxApplication` |
| Servlet `doGet` / `doPost`, request and response | `LedgerServlet`, `BridgeIngestServlet` |
| JSP tags, EL, JSTL | `ledger.jsp` |
| Wrapper-style money | `BigDecimal` on amounts |

## Module 1 — Java fundamentals

| Syllabus line | Where it will live | Proof |
|---|---|---|
| One-dimensional array | `String[] demoVpas` in `Payer`, the seeded account list | `PayerTest` reads the array and builds one payer per entry |
| Two-dimensional array | `boolean[][] inRange` in `MeshTopology`: five phones by five phones. Gossip forwards only across a `true` edge | `MeshTopologyTest`: Alice reaches the bridge in two hops and does not reach a phone marked out of range |
| Enhanced for-loop | The relay walks `inRange[row]` with a for-each | Same topology test |
| `java.lang` String handling | `Vpa` parses `alice@demo` with `split`, `substring`, `equalsIgnoreCase`, `trim`, and `isBlank` | `VpaTest` rejects `alice`, `alice@other`, and blank text |
| Source file and class layout | One public class per file, package line matching the folder. Called out in this plan so the viva can point at `Vpa.java` | File layout itself |
| JVM, bytecode, language features | A short section in the project report, plus `javap -c com.demo.upimesh.model.Vpa` as the viva command. This stays documentation because the running payment path cannot display the JVM | Report paragraph and the `javap` command |

## Module 2 — Object-oriented programming

Build a small type hierarchy around phones and payments. The mesh already has `VirtualDevice`; that class becomes the concrete offline phone.

| Syllabus line | Where it will live | Proof |
|---|---|---|
| Object reference array | `MeshDevice[] phones` held by `MeshTopology` | Topology test checks length 5 |
| Constructor overloading and `this` | `Payer(String vpa)` and `Payer(String vpa, String holderName, String deviceId)`, field assigns through `this` | `PayerTest` |
| `static` block | `Payer` loads the demo VPA table once in `static { ... }` | First call of `Payer.ofVpa` returns Alice without a database |
| Nested / inner class | `MeshPacket.Hop` (packet id, remaining ttl) used when a phone forwards | Gossip test reads `Hop` |
| Inheritance and `super` | `abstract class MeshDevice`; `OfflineDevice` and `BridgeDevice` call `super(deviceId)` | `MeshDeviceTest` |
| Polymorphism | `MeshTopology.forward(MeshDevice from, MeshPacket packet)` calls `from.accept(packet)`. A bridge accepts and also queues an upload | Same test: only `BridgeDevice` produces an upload |
| Abstract class | `MeshDevice` with abstract `boolean hasInternet()` | Compiler-level: the test instantiates only the subclasses |
| Interface | `PacketCarrier` with `hold` and `Iterable<MeshPacket> packets()`. Both device types implement it | Relay code depends on the interface, not on `VirtualDevice` |
| Wrapper classes | Hop count stored as `Integer` inside `MeshPacket.Hop`, compared with `compareTo` | Hop test |
| Packages and subpackages | New types go in the existing packages: `model`, `service`, `jdbc`. No new root package | Package declarations |
| `finalize()` | Current JDKs deprecate `finalize()` for removal. Resource release for the syllabus's "cleanup" outcome lives in `JdbcLedger`: try-with-resources on the `Connection`, and an explicit `finally` that rolls back when `commit` did not run. A comment on that `finally` names `finalize()` as the obsolete term | `JdbcLedger` rollback test still passes; the comment is the viva hook |

## Module 3 — Robustness, exceptions, and multithreading

### Exceptions

| Syllabus line | Where it will live | Proof |
|---|---|---|
| Exception types and control flow | Checked `MeshException` for a bad packet file; unchecked `PaymentException` for business rejection | Tests expect each type |
| `try`, `catch`, `finally`, `throw`, `throws` | `MeshPacketFileStore.restore()` already catches a bad file. Add `finally` around the JDBC connection. `transfer()` declares `throws PaymentException` where the caller must decide. `throw new StalePacketException(...)` from ingestion | `LedgerFlowTest` plus a new rollback test |
| User-defined exceptions | `PaymentException` (abstract) with `InsufficientFundsException`, `UnknownAccountException`, `StalePacketException`. Keep `LedgerConflictException` and `DuplicatePacketException` | `insufficientFundsIsRejectedAndNotSettledAgain` asserts the subclass |

`BridgeIngestionService` keeps today's outcomes: `SETTLED`, `REJECTED`, `DUPLICATE_DROPPED`, `INVALID`. The new exceptions are the inside of that method, and the servlet prints `ex.getMessage()` into the JSP message line.

### Multithreading

This is the main gap the simple version fills. Gossip does not keep a thread alive for each phone.

`gossipOnce` snapshots every phone, then starts one `Thread` per phone. The thread's `run` method only calls `PacketInbox.add`. The caller `take()`s each copy and calls `hold` on the destination phone. `add` and `take` use `wait` and `notifyAll`. The workers never lock a phone, and the caller is the only one that stores a packet, so two phones cannot deadlock. `JdbcLedger` updates the two account rows in sorted VPA order for the same reason.

`IdempotencyConcurrencyTest` is still the settlement-side proof: three threads, one settlement. `ReadableMeshTest` is the mesh-side proof: one gossip round, four copies, the bridge holds the packet.

An unknown account throws `UnknownAccountException`. A stale or future-dated packet throws `StalePacketException` and keeps the claim. A bad packet file throws checked `MeshException`. A short balance is still a committed `REJECTED` row, not an exception. There is no long-lived thread per phone. The optional MySQL settings are in `application-mysql.properties`; the demo database stays the H2 file.

## Module 4 — Files, streams, collections, generics, annotations

| Syllabus line | Where it will live | Proof |
|---|---|---|
| File I/O streams | Keep `MeshPacketFileStore` and `settlements.log`. Add nothing parallel | Existing restore after restart, stated in the run steps |
| Serialization | `MeshPacket` is already `Serializable`. `MeshPacket.Hop` must be `Serializable` too, or `transient` if it is derived | Round-trip test: save, new store, restore, same packet id |
| `List`, `Map`, `Set` | `Set<String> seenPacketIds` on each phone replaces "have I already held this id?" | Relay test: one gossip does not duplicate a packet on the same phone |
| Lambda | `phones.stream().filter(MeshDevice::hasInternet).collect(...)` when collecting bridge uploads | Flush still uploads only `phone-bridge` |
| Generics | `final class PacketInbox<T extends MeshPacket>` wrapping `BlockingQueue<T>` | Relay is declared `PacketInbox<MeshPacket>` |
| Annotations | Custom `@MeshNode(internet = false)` on `OfflineDevice` and `@MeshNode(internet = true)` on `BridgeDevice`. `MeshTopology` reads it with `getAnnotation` when building the phone array. JPA annotations stay on `Account` and `Transaction` | Topology test fails if the annotation is removed from `BridgeDevice` |

## Module 5 — JavaFX and JDBC

| Syllabus line | Where it will live | Proof |
|---|---|---|
| Events and controls | Already on the form buttons. Leave them | Click Inject on the desktop app |
| Menus | `MenuBar` on the JavaFX window: Mesh (Gossip, Flush, Reset) and Help (About, with one sentence on the mesh) | Menu items call the same methods as the buttons |
| JDBC connectivity | `JdbcLedger` stays the only debit/credit path | Existing ledger tests |
| MySQL, as named in lab experiments 10 and 14 | Default run stays the H2 file so a viva machine needs no database install. Add `application-mysql.properties` with `jdbc:mysql://localhost:3306/upimesh` and the same `JdbcLedger` SQL. The run command is `mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` after the database exists | The properties file is the artifact; a live MySQL check is optional and documented as a manual step |

## Module 6 — Servlets

| Syllabus line | Where it will live | Proof |
|---|---|---|
| Lifecycle: `init`, `destroy` | `LedgerServlet.init()` logs the servlet name once. `destroy()` logs shutdown | Server log on startup and on Ctrl+C |
| Deployment descriptor `web.xml` | `src/main/webapp/WEB-INF/web.xml` declares both servlets, their mappings (`/ledger`, `/servlet/ingest`), and `<session-timeout>30</session-timeout>`. `ServletConfig` keeps creating the Spring-managed instances (so JDBC injection still works) and the `web.xml` entries are the descriptor the syllabus names. The Java registration and the XML must use the same URL patterns | Open `web.xml` in the viva; GET `/ledger` still renders |
| Request and response | Already. `doPost` keeps reading parameters; `doGet` keeps writing the forward to the JSP | Form submit |
| Session tracking | `HttpSession` attribute `lastOutcome` and an integer `visitCount`. The JSP prints both. Reset does not clear the session | Submit once, refresh, the visit count increases and the last outcome remains |

## Module 7 — JavaServer Pages

| Syllabus line | Where it will live | Proof |
|---|---|---|
| Tags and EL | Keep the current `forEach` and `${tx.status}` | Ledger page |
| Custom tag | `RupeeTag` plus `WEB-INF/mesh.tld`. `ledger.jsp` prints amounts with `<mesh:rupee value="${tx.amount}"/>` so the table shows a rupee figure from the tag, not from the servlet | Page source and the rendered amount |
| JSP with a JavaBean | `LedgerPage` bean (visit count, last outcome, account list). `ledger.jsp` uses `<jsp:useBean id="pageBean" class="com.demo.upimesh.web.LedgerPage" scope="request"/>` and `<jsp:getProperty/>` for the visit count. The servlet fills that bean before the forward | The visit line on the page comes from `getProperty` |

Lab experiment 13 ("order management") is this ledger: the order is the payment, the rows are the transactions. No second web app.

## Module 8 — Contemporary issues

The PDF's Module 8 cell lists 2 hours and no topics. The plan does not invent a Java feature for it. Add `docs/contemporary-issues.md` (about one page) tied to this system:

- An offline packet is a promise. The receiver cannot see the sender's balance until a bridge uploads.
- Two different packets can double-spend; the ledger rejects the one that no longer fits.
- The demo PIN is hashed and stored. It is not checked against a bank.
- `finalize()` is obsolete; the project releases the JDBC connection in `finally`.
- The ledger session cookie is fine for this demo. A public bridge endpoint would want a token or mutual TLS.

## Lab experiments, mapped

| # | Syllabus experiment | Proof inside this project |
|---|---|---|
| 1 | Multidimensional arrays and loops | `boolean[][] inRange` and `MeshTopologyTest` |
| 2 | String handling | `Vpa` and `VpaTest` |
| 3 | Inheritance | `OfflineDevice` / `BridgeDevice` extend `MeshDevice` |
| 4 | User-defined packages | Existing `com.demo.upimesh.*` tree, plus `Vpa` and `MeshDevice` in those packages |
| 5 | Exception handling | `UnknownAccountException`, `StalePacketException`, checked `MeshException`, and `finally` in `JdbcLedger.transfer` |
| 6 | Threads | One `Thread` per phone in `gossipOnce`, `PacketInbox` `wait`/`notify`, and the three-bridge test |
| 7 | File handling | `packets.ser` and `settlements.log` |
| 8 | Collections | `PacketInbox`, `Set` of seen packet ids, stream filter for bridges |
| 9 | JavaFX GUI | Existing window plus the Mesh and Help menus |
| 10 | JDBC (syllabus names MySQL) | `JdbcLedger` on the H2 file. Optional `application-mysql.properties` |
| 11 | Servlets for banking tasks | `LedgerServlet.doPost` settles a payment; `BridgeIngestServlet.doPost` accepts a packet |
| 12 | JSP request and response | Form post and the redirected message |
| 13 | JSP order management | The transaction table is the order list |
| 14 | JSP plus JDBC storage | `doGet` reads accounts and transactions through `JdbcLedger` into the page |
| 15 | JSP with a JavaBean | `<jsp:useBean>` on `LedgerPage` |

## What to open in a viva

1. `mvnw test` — `ReadableMeshTest`, `LedgerFlowTest`, and the three-bridge test.
2. `mvnw spring-boot:run`, open `/ledger`, settle a payment, refresh, and show the visit count and `Rs` amounts.
3. Show `web.xml`, `MeshSimulatorService.gossipOnce`, and the sorted-VPA update in `JdbcLedger`.
4. Open the JavaFX window with `mvnw javafx:run` only after the server on port 8080 is stopped. The Mesh menu calls gossip, flush, and reset.
