# Project context — servoy-extensions (Java)

This is the Servoy **extensions** checkout: the server/client plugins and beans that ship
with Servoy (mail, http, file, rest_ws, broadcaster/datanotifybroadcaster, pdf, workflows,
oauth, etc.). SDD runs in Java repos here.

## Checkout layout

Workspace root: `servoy-extensions/`. Relevant projects (each an Eclipse `.project`):

- **`com.servoy.extensions`** — the main plugin bundle. All production plugin code lives
  here under `src/com/servoy/extensions/plugins/<pluginname>/`. This is almost always the
  project you edit. Eclipse project name (for the Eclipse MCP tools): `com.servoy.extensions`.
- **`com.servoy.extensions.test`** — the test module (plain Maven, not Tycho). Tests live
  under `src/test/java/com/servoy/extensions/plugins/<pluginname>/`, mirroring the
  production package. Add new unit tests here, in the package matching the code under test.
- **`servoy_shared`**, **`servoy_base`** — upstream Servoy API (interfaces such as
  `IServerPlugin`, `IServerAccess`, `IDataNotifyService`, `IDataNotifyListener`,
  `BroadcastFilter`, `IDataSet`, `com.servoy.j2db.util.Debug`/`Utils`). **Read-only here** —
  these are provided by the core (`servoy_shared` / `j2db_server`) checkout, not changed in
  this repo. If the real fix needs a core change, raise a separate SVY case against the core
  repo rather than editing these projects from here.
- `com.servoy.extensions.workflow`, `org.eclipse.dltk.javascript.rhino` — unrelated to this
  work.

## Stack / build

- **Java 21** (`maven.compiler.source/target` = 21), encoding UTF-8.
- `com.servoy.extensions` is built with **Maven** (the giant `pom.xml` assembles plugin/bean
  jars, copies third-party jars into `target/plugins_beans/plugins/...`, and signs them). It
  is **not** a plain quick build: it pulls many third-party artifacts and signs jars, so a
  full local `mvn package` is heavy and usually unnecessary for a single-plugin change.
- `com.servoy.extensions.test` is a lightweight Maven module: JUnit via surefire, depends on
  `com.servoy.extensions`. Test includes pattern: `**/*Test.java`.
- RabbitMQ client: `com.rabbitmq:amqp-client` version is the pom property **`amqp.version`**
  (currently `5.34.0`). The broadcaster plugin builds against this.

### Preferred verification (fast, authoritative)

Use the **Eclipse MCP** against the live workspace rather than a Maven build whenever
possible — it is the source of truth for Java here and is always in sync with edits:

- Compile check: `eclipse-ide` `buildWorkspace` (or rely on incremental) then
  `getCompilationErrors` scoped to project `com.servoy.extensions` (and
  `com.servoy.extensions.test` for tests). Clean markers = compiles.
- Run tests: `eclipse-runner` to run the JUnit test class/method in
  `com.servoy.extensions.test`.
- A full Tycho/Maven reactor build is generally **not** run locally for a single-plugin
  change (slow, needs signing material and many third-party jars); CI does that. Prefer
  "Eclipse compile check only" or "affected plugin + its tests" at the pre-commit gate and
  say so.

## Testing conventions

- **JUnit 4** (`org.junit.Test`, `org.junit.Assert.*`) — that is what the existing test
  (`RestWSServletMultipartDetectionTest`) uses. Do not introduce JUnit 5 or Mockito unless a
  test genuinely needs it and you note the new dependency in the test pom; default to plain
  JUnit 4 with hand-written fakes/stubs.
- Test class name ends in `Test` (surefire include `**/*Test.java`).
- Place tests in `com.servoy.extensions.test` under the package of the class under test,
  e.g. broadcaster tests → `src/test/java/com/servoy/extensions/plugins/broadcaster/`.
- The RabbitMQ `Channel`/`Connection` are interfaces, so broadcaster logic can be tested
  with hand-written fakes (a fake `Channel` whose `basicPublish` throws / records sizes, a
  fake `Connection` toggling `isOpen()`), no live broker required. Keep unit tests broker-free.

## Code conventions

- Every source file starts with the Servoy **AGPL v3 header** (copy it from an existing file
  in the same project; keep the original copyright year on existing files).
- Tabs for indentation (match surrounding code).
- Logging/diagnostics go through `com.servoy.j2db.util.Debug` (`Debug.log/warn/error`), not
  `System.out` or a logging framework. `@SuppressWarnings("nls")` is used on classes with
  many string literals, as in the existing broadcaster classes.
- Server plugins implement `com.servoy.j2db.plugins.IServerPlugin`; settings come from
  `IServerAccess.getSettings().getProperty(...)` and every setting is documented in
  `getRequiredPropertyNames()`. If you add a new `amqpbroadcaster.*` setting, add its
  description there too.
- Preserve comments that explain intent/behaviour (e.g. "when a connection is recovered we
  don't know what we missed so the only thing to do is a full flush").

## The code in scope for this work

Plugin package: `com.servoy.extensions/src/com/servoy/extensions/plugins/broadcaster/`
- `DataNotifyBroadCaster` — `IServerPlugin`; builds the single `Connection` + single
  `Channel`, declares the `fanout` exchange, registers the consumer and the
  `DataNotifyListener`. Only a connection-level `RecoveryListener` exists today; there is no
  channel-level shutdown/recovery handling.
- `DataNotifyListener` — publish side; serializes `NotifyData` and `channel.basicPublish`es,
  with a `failedList` that only drains on connection recovery or a healthy publish.
- `NotifyData` (Serializable payload), `BroadcastMessage`, `IBroadcastMessageConsumer`,
  `IBroadcastMessageSender`.

Relevant core facts (verified, read-only from here):
- Core `SQLEngine.DistributeDataChange.run()` already strips large **byte[]** blobs
  (`> 16` bytes → `ValueFactory.createBlobMarkerValue()`) but **only for single-row INSERTs**,
  and does **not** cap large **String** values or large **pk datasets**. So the oversized
  (~22 MB) databroadcast that RabbitMQ rejects with `precondition_failed` can come from a big
  text column or a bulk pk set — the plugin must guard on the actual serialized size, not on
  column type.

## Git / commit

- Branch: `lts_2026` (origin `github.com/Servoy/servoy-extensions`). Do not push to a shared
  branch without being asked; commit locally.
- Commit messages **must** end with ` [ai]` and include the Jira key, per repo AGENTS.md:
  ```
  SVY-XXXXX <short description> [ai]

  - bullets

  Co-Authored-By: opencode <noreply@opencode.ai>
  ```
- Never amend a pushed commit; prefer a new commit. Keep behaviour-describing comments.
