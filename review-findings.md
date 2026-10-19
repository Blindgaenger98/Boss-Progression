# Review-Findings — BossProgression

## 1. Metadaten

| Feld | Wert |
|---|---|
| **Datum** | 2026-10-19 |
| **Projekt** | BossProgression — Paper-Plugin (Minecraft 1.26.3 / Paper `26.3.build.200-stable`), Java 25, Maven |
| **Umfang** | 5 thematische Code-Reviews, nacheinander ausgeführt |
| **Modus** | Rein read-only (keine Codeänderungen; diese Datei ist das einzige Artefakt) |

---

## 2. Übersichts-Tabelle der 5 Reviews

| # | Reviewer | Thema | Geprüfte Bereiche | Kritisch | Warnungen | Hinweise |
|---|---|---|---|---:|---:|---:|
| 1 | Config & Data-Layer | Konfiguration, Persistenz, Konventionen | `ConfigManager`, `DataStore`, `BossTier`, `ReviveItemConfig`, `HardmodeConfig`, `messages.yml`/`config.yml`-Konsistenz, zugehörige Tests | 1 | 6 | 7 |
| 2 | Progression / Permadeath / Hardmode | Boss-Death, Drops, Strafen, Hardmode, Struktur-/Raid-Events | `BossDeathListener`, `PermadeathListener`, `HardmodeListener`, `HardmodeManager`, `PenaltyApplier`, `SpawnGuardListener`, `RaidEventListener`, `StructureEventListener` | 3 | 6 | 6 |
| 3 | Revive & Altar | Revive-Item-Validierung, Konsum, Altar-GUI | `ReviveItemValidator`, `ReviveManager`, `AltarManager`, `AltarListener`, `ReviveItemValidatorTest`, Altar-Testabdeckung | 5 | 9 | 5 |
| 4 | Commands / Messaging / Hooks / Bootstrap | Befehle, Messages, Hook-Framework, Plugin-Lifecycle | 4 Commands, `Messages`, `BossHookManager` + Hooks, `BossProgressionPlugin` (onEnable/onDisable), `plugin.yml` | 1 | 7 | 8 |
| 5 | Testsuite & Build | Testabdeckung, Testqualität, `pom.xml` | alle Testklassen, `PluginTestBase`, `pom.xml`, Dokumentation (AGENTS.md, Plan) | 10 | 7 | 3 |

> Die Zählung in dieser Tabelle ist die Summe der im Folgenden aufgeführten Einträge pro Schweregrad (Reviewer 1: 1×Kritisch-Block aus ReviveItemConfig-Kette, 6 Warnungen, 7 Hinweise).

---

## 3. Konsolidierte Top-10-Kritikliste (dedupliziert)

| # | Priorität | Betroffene Dateien | Beschreibung | Empfohlener Fix |
|---|---|---|---|---|
| 1 | 🔴 Kritisch | `ReviveItemValidator.java:94` (Write bei `ReviveItemValidator.java:101`) | **Unbegrenzte Revives — `consumeOne()` schreibt nur in Array-Kopie**: `contents[i] = null` wird nur in der Kopie von `getStorageContents()` gesetzt; das echte Inventar behält das Item (bei amount==1 der Normalfall, da `BossDeathListener` immer `createItem(plugin, 1)` erzeugt). | Signatur ändern zu `(Inventory, int slot)` + `inv.setItem(...)`. |
| 2 | 🔴 Kritisch | `AltarManager.java:154-156`, `ReviveManager.java:70-71` | **Doppelkonsumption im Altar-Flow**: `AltarManager.handleGuiClick` (L154–156) konsumiert das Item, dann ruft L162 `reviveManager.revive()`, das erneut konsumiert → bei exakt 1 Item: `NO_ITEM` + Item verloren. | `consume`-Parameter zentralisieren (Konsum nur an einer Stelle). |
| 3 | 🔴 Kritisch | `BossDeathListener.java:57`, `PermadeathListener.java:41` | **`getDrops()` ist read-only (Paper 26) → Crash bei jedem Boss-Tod**: `BossDeathListener.java:57` `getDrops().add(...)`; zweite Stelle `PermadeathListener.java:41` `getDrops().addAll(...)` — dort zusätzlich: null-Slots aus `getStorageContents()`, nur 36 Haupt-Slots, Rüstung/Offhand weder dropped noch cleared. | `dropItemNaturally(...)` wie `RaidEventListener.java:83` / `StructureEventListener.java:144`. Tests sind grün, weil MockBukkit dies nicht erzwingt (`BossDeathListenerTest.java:35`). |
| 4 | 🔴 Kritisch | `BossProgressionPlugin.java:104-106`, `DataStore.java:117`, `DataStore.java:73` | **`/reload`-Datenverlust — Auto-Save-Timer nie gestoppt**: `onDisable` ruft nur `dataStore.save()`, nie `DataStore.shutdown()` (toter Code); der Timer startet in `DataStore.load` (L73). Der alte Timer überschreibt nach Reload die frisch geladenen Daten mit dem Staltem Zustand. | `save()` → `shutdown()` in `onDisable`. |
| 5 | 🔴 Kritisch | `HardmodeListener.java:45`, `HardmodeListener.java:50` | **Food-Nerf logisch unmöglich**: Bedingung `player.getFoodLevel() == 20 && event.getFoodLevel() > player.getFoodLevel()` ist unerreichbar. | Invertieren: `event.getFoodLevel() == 20 && player.getFoodLevel() < 20`. |
| 6 | 🔴 Kritisch | `AltarManager.java:33`, `AltarManager.java:94` | **`slotOwners`-Kollision zwischen Spielern**: gemeinsames `Map<Integer,UUID>`; `openGui()` (L94) ruft `slotOwners.clear()` → Spieler B löscht das Mapping von Spieler A → Revive an falschem Ziel. | Pro Spieler ein eigenes `InventoryHolder` (löst auch W4). |
| 7 | 🔴 Kritisch | `ReviveItemValidator.java:84-86`, `ReviveItemConfig.java:63`, `ReviveItemValidatorTest.java:78-79` | **Display-Name-Fallback tot (String vs Component)**: `String.equals(Component)` ist immer false; `ReviveItemConfig.java:63` nutzt deprecated `setDisplayName(String)` + `ChatColor.translateAlternateColorCodes` (NPE-Risiko bei null displayName); der Test `displayNameFallbackMatches` behauptet den toten Pfad. | `LegacyComponentSerializer.legacySection().deserialize` + Component-Vergleich + null-Validierung beim Load (Component-Migration zusammen über 3 Dateien). |
| 8 | 🔴 Kritisch | `SpawnGuardListener.java:70`, `PenaltyApplier.java:42` | **Kein Penalty-Cooldown**: vollste Strafe (25 % Max-Health + Wither + Slowness) bei JEDEM Interact → 4 Klicks = Selbstkill. | Per-UUID-Timestamp als Cooldown. |
| 9 | 🔴 Kritisch | `ProgressionManager.java:92`, `HardmodeManager.java:77` | **`setTier(0)` Ghosts + `HardmodeManager.reset()` nie aufgerufen**: `setTier` ruft nie `reset()` (die Methode wird nie irgendwo aufgerufen; die Javadoc verspricht `/bossadmin settier`); `setTier(0)` leert nicht `deadPlayers` → Geisterspieler. | `reset()` in `setTier` aufrufen + `deadPlayers` bei Tier 0 leeren. |
| 10 | 🔴 Kritisch | `AltarCommand.java:26-38`, `plugin.yml:31-33`, `BossAdminCommand.java:122-128`, `ConfigManager.java:38` | **`/altar` ohne Permission-Check**: `AltarCommand` prüft nicht `bossplugin.altar` (deklariert in `plugin.yml`). Zusätzlich ignoriert `handleReload` den Booleschen `load()`-Wert → falscher Erfolg; und Reload ist nicht atomar (Javadoc behauptet atomar → Hybrid-Zustand-Risiko). | Permission-Check ergänzen; `load()`-Return-Wert auswerten; Reload atomar machen. |

---

## 4. Reviewer-Detailberichte

### 4.1 Reviewer 1: Config & Data-Layer

**Verifizierte Invarianten**
- `DataStore` = Single Source of Truth — nur `ProgressionManager.java:59` / `ProgressionManager.java:97` mutiert das Tier.
- PDC-Keys sind bare (keine `:`); `NamespacedKey`-Konstruktion konsistent.

**Konventionsverstoß**
- `ConfigManager.java:205` verwendet `getX()`-Accessoren (AGENTS.md verlangt bare Accessors; nur `DataStore` ist ausgenommen).

#### Kritisch
- `ReviveItemConfig.java:63`: deprecated `setDisplayName(String)` + `ChatColor.translateAlternateColorCodes` → Paper 26 ist Component-basiert. Folge: `ReviveItemValidator.java:86` `String.equals(Component)` ist immer false (Display-Name-Fallback, Prioritäts-Pfad 3, tot).

#### Warnungen
1. `ConfigManager.java:38` — Reload nicht atomar (Javadoc behauptet atomar; Hybrid-Zustand-Risiko).
2. `ReviveItemConfig.java:59` — keine AIR-Material-Validierung.
3. `ConfigManager.java:141` — keine `pdc-key`-Charset-Validierung beim Load.
4. `ConfigManager.java:68` — defekte `boss-tiers`-Entries werden still übersprungen.
5. 4 ungenutzte `messages.yml`-Keys: `premature-attempt.penalty-applied` (L17), `raid.penalized` (L25), `permadeath.wrong-item` (L45), `altar.no-altar-block` (L56).
6. `DataStore.java:117` — `shutdown()` toter Code (`onDisable` ruft nur `save()`; Auto-Save-Task L73 nie gecancelt).

#### Hinweise
1. `BossTier.java:51` — totes `getTierName()` mit hardcodierter String.
2. `ConfigManager.java:159` — undokumentierter „altar-system"-Legacy-Fallback.
3. `DataStore.java:87` — `HashSet` → nicht-deterministische `dead_players`-Reihenfolge.
4. `DataStore.java:19` — widersprüchliches Javadoc (async-Claim, tatsächlich synchron im Main-Thread).
5. `ConfigManager.java:126` / `ConfigManager.java:140` — hardcodierte Defaults („&cBoss", „&aRevive Item").
6. `BossTier.java:37` — totes null-normalizing.
7. deprecated `ChatColor` in `ReviveItemConfig.java:63`.

#### Testlücken
- `HardmodeConfig.multiplierForTier`, `ReviveItemConfig.createItem` ungetestet.
- `DataStoreTest` fehlt: invalider UUID (`DataStore.java:53`), fehlende World (L60), `markDirty`-Debounce.
- `ConfigManagerPhase2Test` fehlt: doppelte Tiers (L150), fehlendes `boss-tiers`, invalider Entity-Type/Material, `auto-save-minutes` Clamp (L48), altar/penalty/hardmode-Load-Pfade.

#### Keine Findings (geprüft, OK)
- `messages.yml`-Platzhalter korrekt.
- `config.yml` ↔ Java-Defaults konsistent.
- `DataStore`-Persistenz sicher (Clamp, IOException hält `dirty=true`, defensive copies, `Location.clone`).

#### Handoffs an andere Reviewer
- revive: String vs Component
- progression: `RaidEventListener.java:56` hardcoded
- command: usage strings ab L131
- hook: MythicMobs-Placeholder
- general: onDisable; doppeltes `markDirty` in `BossAdminCommand` L113 (harmlos)

---

### 4.2 Reviewer 2: Progression / Permadeath / Hardmode

#### Kritisch
- **K1** — `BossDeathListener.java:57` `getDrops().add()`: read-only in Paper 26 → Crash bei JEDEM Boss-Tod. Test `BossDeathListenerTest.java:35` grün, weil MockBukkit nicht erzwingt. Fix: `dropItemNaturally` (wie `RaidEventListener.java:83`, `StructureEventListener.java:144`).
- **K2** — `PermadeathListener.java:41` `getDrops().addAll(...)`: zusätzlich null-Slots aus `getStorageContents()` + nur 36 Haupt-Slots (Rüstung/Offhand weder dropped noch cleared).
- **K3** — `HardmodeListener.java:50` Food-Nerf-Bedingung logisch unmöglich (s. Top-10 #5); Fix: invertieren.

#### Warnungen
1. **W1** — `PenaltyApplier.java:60` `player.damage(double)` ohne `DamageSource` (Paper 26); kein null-Check auf `getAttribute(MAX_HEALTH)` (L59).
2. **W2** — Kein Penalty-Cooldown (`SpawnGuardListener.java:70` → `PenaltyApplier.java:42`): volle Strafe bei jedem Interact (25 % Max-Health + Wither + Slowness) → 4 Klicks Selbstkill. Fix: per-UUID-Timestamp.
3. **W3** — `ProgressionManager.java:92` `setTier` ruft nie `HardmodeManager.reset()` (die Methode wird nie irgendwo aufgerufen; die Javadoc `HardmodeManager.java:77` benennt `/bossadmin settier`); stale `baseValues` nach settier; `setTier(0)` leert nicht `deadPlayers` → Ghosts.
4. **W4** — Hardcodierte Strings: `RaidEventListener.java:56` + `PenaltyApplier.java:66` („&c… is not ready yet!" Actionbar); ungenutzte Keys `premature-attempt.penalty-applied` (L17), `raid.penalized` (L25).
5. **W5** — `StructureEventListener.java:135` stale `lastStates`-Entries (im clear-Zweig aus `trackedSpawners` entfernt, aber nicht aus `lastStates`). Fix: `lastStates.remove(loc)`.
6. **W6** — `RaidEventListener.java:62` fehlendes `ignoreCancelled` (nicht cancelbares Event, nur Hausstil).

#### Hinweise
1. **H1** — AGENTS.md-Dokudrift: referenziert einen `@Disabled`-Chunk-Test in `SpawnGuardListenerTest`, der nicht existiert (0 Treffer).
2. **H2** — Testlücken: null Tests für das gesamte hardmode-Package; kein `PenaltyApplierTest` (Admin-Exemption L47, Radius, `getNearbyPlayers((int)radius)`-Trunkierung L34); `BossDeathListenerTest` nur Happy-Path.
3. **H3** — Struktur-Fortschritt nur in-memory (L116), bei Restart verloren.
4. **H4** — Radius-Trunkierung L34.
5. **H5** — `HardmodeManager` listener-privat (`HardmodeListener.java:26`) → `reset()` unerreichbar; sollte ein Plugin-Accessor sein.
6. **H6** — `PermadeathListener.java:41` vollständig qualifizierte `java.util.Arrays` inline.

#### Verifizierte Vorerkenntnisse
- #1 bestätigt + 2. Stelle gefunden (`PenaltyApplier.java:66`).
- #2 bestätigt (2 Verletzer).
- #3 weitergeleitet.
- #4: `bossplugin.admin` konsistent (`PenaltyApplier.java:47`, `StructureEventListener.java:101`).

#### Stärken
- Invariante hält: nur `ProgressionManager` mutiert das Tier.
- `@EventHandler(HIGH, ignoreCancelled)` weitgehend konsistent.
- Hook-Veto-Reihenfolge sauber (`SpawnGuardListener.java:51`).
- `HardmodeManager`-Compound-Scaling-Cache korrekt.
- `StructureEventListener`-Polling (40 Ticks) pragmatisch.
- Test-Setup solide.

#### Handoffs
- revive/altar: `markAlive`, ungenutzte Keys, Component-Migration
- `BossAdminCommand`: fehlender `reset()`-Call, Ghost bei settier 0
- hook: `onBossDeath` vor `advance()` in `BossDeathListener.java:48` — try/catch prüfen
- data/bootstrap: shutdown, markDirty-Race
- cross-cutting: MockBukkit read-only-drops Wrapper

---

### 4.3 Reviewer 3: Revive & Altar

#### Kritisch
- **K1** — `ReviveItemValidator.consumeOne()` (L94, Write L101): `contents[i] = null` nur in Array-Kopie von `getStorageContents()` → echtes Inventar behält das Item → unbegrenzte Revives (amount==1 ist der Normalfall, `BossDeathListener` erzeugt immer `createItem(plugin, 1)`). Fix: Signatur `(Inventory, int slot)` + `inv.setItem`.
- **K2** — Doppelkonsumption im Altar-Flow: `AltarManager.handleGuiClick` L154–156 konsumiert, dann L162 `reviveManager.revive()` konsumiert erneut (`ReviveManager.java:70-71`) → bei exakt 1 Item: `NO_ITEM` + Item verloren. Fix: `consume`-Parameter oder Zentralisierung.
- **K3** — Display-Name-Fallback tot: `String.equals(Component)` ist immer false (L84–86); `createItem` (`ReviveItemConfig.java:63`) deprecated API; NPE-Risiko bei null displayName (`ChatColor.translateAlternateColorCodes('&', null)`). Fix: `LegacyComponentSerializer.legacySection().deserialize` + Component-Vergleich + null-Validierung beim Load.
- **K4** — Test `displayNameFallbackMatches` (`ReviveItemValidatorTest.java:79`) behauptet den toten Pfad.
- **K5** — Gemeinsames `slotOwners` `Map<Integer,UUID>` (`AltarManager.java:33`); `openGui()` (L94) ruft `slotOwners.clear()` → Spieler B löscht das Mapping von Spieler A → cross-player falsches Revive-Ziel. Fix: pro Spieler ein `InventoryHolder` (löst auch W4).

#### Warnungen
1. **W1** — `AltarListener.java:41` hardcodiert „§cThe altar block was changed - ..."; ungenutzter Key `altar.no-altar-block` (`messages.yml` L56).
2. **W2** — Ungenutzter Key `permadeath.wrong-item` (L45) — `consumeOne` liefert nur `boolean`.
3. **W3** — Message `permadeath.revived` verspricht Invulnerability, Code gibt nur REGENERATION (10 s) + SPEED (Dauer = `INVULNERABILITY_TICKS/2` = 5 s); `INVULNERABILITY_TICKS` (L39) missbräuchlich für SPEED (`ReviveManager.java:82-85`).
4. **W4** — deprecated `InventoryView.getTitle()` (`AltarListener.java:52`) + Title-Kollision + ALLE Klicks gecancelt (untere Reihe blockiert).
5. **W5** — `setDisplayName(String)` deprecated (`AltarManager.java:107`); >27 dead players werden still verworfen (`if (slot >= 27) break`).
6. **W6** — Auto-Close-Task-Leak (`AltarManager.java:120-127`): `runTaskTimer` nie gecancelt bei normalem Close (`cancelOpenTask` L175 existiert, aber es gibt keinen `InventoryCloseEvent`-Handler); NPE wenn `setaltar` null setzt.
7. **W7** — Legacy-APIs (`ReviveManager.java:82-91`): `PotionEffectType.getByName` (deprecated, null-able), `Sound.ENTITY_ENDER_DRAGON_GROWL`, `Particle.HAPPY_VILLAGER`. Fix: `PotionEffectTypes` / `Sound.EnderDragon.GROWL` / `ParticleTypes`.
8. **W8** — Ghost-Leak bei Tier 0 ist ein Dead-End: `/revive` → `NO_ITEM` (kein Tier-0-Item), Altar-GUI abgelehnt, `/bossadmin revive` Force funktioniert (kein Konsum, `ReviveManager` L60–67); `onPlayerJoin`-Recovery (`PermadeathListener.java:58`) forzt erneut Spectator, aber es gibt keinen Ausweg aus der Falle.
9. **W9** — Doppeltes `markDirty` (`BossAdminCommand.handleSetAltar` L112–113; `DataStore.setAltarLocation` L164 ruft bereits intern `markDirty`) → L113 redundant.

#### Hinweise
1. **H1** — `matchesCurrentTier`/`findMatch` ohne Production-Caller (L33, L114).
2. **H2** — `meta==null`-Zweig fast tot (L62, AIR gefiltert L55).
3. **H3** — `resolveDestination` Javadoc/Code-Mismatch (L121).
4. **H4** — `getOfflinePlayer(uuid)` Name null in MockBukkit → „?".
5. **H5** — `consumeOneDecrementsStack` (L88) nutzt ein freies `ItemStack[]` → fängt K1 nie.

#### Testlücken
- `ReviveItemValidatorTest` (8 Tests) fehlt: AIR/null, stale-tier Item, Prioritätsreihenfolge, null displayName, `consumeOne` mit echtem PlayerMock-Inventar, amount==1 → AIR.
- KEINE Altar-Tests überhaupt: `isAltarBlock`-Varianten, `openGui`-Varianten (inkl. >27), `handleGuiClick` (inkl. `NO_ITEM`-mit-Verlust assert), zwei-Spieler-Kollision, auto-close.

#### Stärken
- Saubere Architektur, immutable Configs.
- DataStore-Debounce, `Location.clone`.
- `ReviveResult`-Enum + force-Flag.
- Korrekte PDC-Keys.
- Strings via `Messages`.
- `onPlayerJoin`-Ghost-Recovery, `isAltarBlock` korrekt.
- EventHandler-Konvention.

#### Handoffs
- command: hardcodierte usage strings — `ReviveCommand.java:40`, `BossAdminCommand` L56/63/71/78/131-134
- hook: MythicMobs pending, `onBossDeath`-Reihenfolge
- bootstrap: `onDisable`-Reihenfolge OK (stopPolling → close); `saveResource("messages.yml", false)` korrekt
- data: markDirty-Task-Stacking → Coalescing
- progression-Kontext: `drops.add` außerhalb des Scopes

---

### 4.4 Reviewer 4: Commands / Messaging / Hooks / Bootstrap

**Verifizierter Bootstrap-Ablauf (`onEnable`)**
`saveDefaultConfig`+`saveResource` → `Messages` → `ConfigManager.load` (Fehler → `disablePlugin`) → `DataStore.load` (Auto-Save-Timer) → `ProgressionManager`, `PenaltyApplier` → 6× `registerEvents` + `startPolling` → `AltarManager`+`AltarListener` → 4× `registerCommand` (plugin.yml ↔ Code in sync ✓) → `BossHookManager.discoverAndEnable`.

#### Kritisch
- **K1** — `BossProgressionPlugin.java:104-106`: `onDisable` cancelt den Auto-Save-Timer nie (nur `dataStore.save()`, nie `DataStore.shutdown()` L117; Timer aus `DataStore.load` L73; `autoSaveTaskId` nie gecancelt) → bei `/reload` überschreibt der alte Timer das frisch geladene `data.yml` mit dem Staltem Zustand → stiller Datenverlust. Fix: `save()` → `shutdown()`. Der Rest der `onDisable`-Reihenfolge ist korrekt (stopPolling → hookManager.disableAll → altarManager.close → save).

#### Warnungen
1. **W1** — `AltarCommand.java:26-38`: fehlender `bossplugin.altar`-Permission-Check (deklariert in `plugin.yml` L31–33).
2. **W2** — Hardcodierte Strings: `ReviveCommand.java:40`; `BossAdminCommand.java:56, 63, 71-72, 78, 131-134` → in `messages.yml` mit Platzhaltern.
3. **W3** — `handleReload` ignoriert den Booleschen `load()`-Wert (`BossAdminCommand.java:122-128`) → falscher Erfolg.
4. **W4** — `plugin.yml:4` `api-version` „1.21" veraltet (Ziel 1.26.3).
5. **W5** — `BossStatusCommand.java:57-70`: Tab-Completion schlägt Boss-Namen vor, aber der Command ignoriert Args + fehlender Permission-Check in `onTabComplete`.
6. **W6** — `ReviveCommand.java:78-83`: `getOfflinePlayer(UUID)` blockierender Lookup im Tab-Completion Main-Thread → `getOnlinePlayers` + `isDead` nutzen (wie `BossAdminCommand.java:165-174`).
7. **W7** — `BossAdminCommand.java:92` `new ReviveManager(plugin)` pro Aufruf (inkonsistente Ownership) + Force-Revive LEBENDER Spieler ohne NOT_DEAD-Check.

#### Hinweise
1. **H1** — Doppeltes `markDirty` (`BossAdminCommand` L112–113) + generelles markDirty-per-call Task-Stacking (`DataStore.java:108`) → pendingSaveTask-Cache.
2. **H2** — `BossHookManager.hookFor()` toter Code (L62–64).
3. **H3** — Fehlender Message-Key wird nicht geloggt (`Messages.java:38, 44, 53, 59`) → `logger.warning`.
4. **H4** — `yaml`-Feld in `Messages` bei `reload()` nicht `volatile` (`Messages.java:21-34`), derzeit nur Main-Thread.
5. **H5** — `registerCommand` still bei plugin.yml-Mismatch (`BossProgressionPlugin.java:110-116`) → `logger.severe`.
6. **H6** — `onEnable` ohne Fehler-Recovery für unerwartete Exceptions (L42–91) → try/catch + `disablePlugin`.
7. **H7** — Unused Import in `AltarCommand` L8 (`Player`).
8. **H8** — MythicMobsHook-Registrierung prüft nur `isPluginEnabled`, nicht das API-Vorhandensein (`BossHookManager.java:35-37`) — Phase-2-Erinnerung.

#### Verifizierte Vorerkenntnisse
- R1(b) bestätigt + kritisch (K1).
- R1(c) bestätigt + 5. hardcodierter String (`AltarListener.java:41`).
- R2(a) bestätigt, aber WENIGER kritisch (baseValues cacht unskalierte Werte → Scaling bleibt nach settier korrekt; `reset` nur nach Vanilla-Attribute-Änderungen nötig) → auf Hinweis heruntergestuft.
- R2(b) VERWORFEN: `try/catch(Throwable)` korrekt in `BossHookManager.onBossDeath:84-92` + `anyVetoSpawn:68-79`.
- R3(a) bestätigt + erweitert (W2).
- R3(b) bestätigt (H1).
- R3(c) Reihenfolge korrekt.
- R3(d) `saveResource(false)` korrekt.
- R3(e) bestätigt (H1).

#### Stärken
- Vollständige plugin.yml ↔ Code-Sync (alle 4 Commands).
- Robustes `Messages`-Design.
- Exakter Hook-Vertrag (catch `Throwable` inkl. LinkageError).
- Korrekte `onEnable`-Reihenfolge.
- `BossAdminCommand`-Tab-Completion best-im-project.

#### Empfehlungen an Reviewer 5
- null Tests für command/Messages/hook-Klassen.
- `PluginTestBase` lädt das volle Plugin → Command-Tests trivial (`server.getCommand().execute` + `player.nextMessage()`).
- Prioritäten: `ReviveCommand`-Pfade, `BossAdminCommand.handleSetTier`-Rand, `Messages`-Fallback, `BossHookManager` mit werfendem Hook.
- K1 = Top-Regression-Test-Kandidat (`getPendingTasks` nach disable).
- api-version vor `mvn verify`.
- pom-Version 1.0.0 konsistent.

---

### 4.5 Reviewer 5: Testsuite & Build

> Hinweis: kein Terminal → `mvn` nicht ausgeführt; statische pom-Einschätzung (reproduzierbar: fixe Versionen, `release=25`, Paper-Repo).

#### Kritisch (10)
1. **consumeOne-Array-Kopie-Bug ungetestet auf Production-Pfad** (`ReviveManager.java:71`, `AltarManager.java:156`; Test L89 nutzt ein freies Array; `ReviveManager`/`AltarManager` haben 0 Tests).
2. **`getDrops()` read-only wird nie erzwungen** (`BossDeathListenerTest.java:35` mutable ArrayList; `inSequenceDeathAddsReviveItemToDrops` L61 validiert den Bug).
3. `PermadeathListener.java:40` — read-only-Mutation auf ungetestetem Pfad (`PermadeathListenerTest.java:29` nur Default `clear-inventory:false`).
4. Hardmode-Food-Nerf unmöglich (`HardmodeListener.java:45`) + 0 Tests für das gesamte hardmode-Package.
5. `onDisable`/`shutdown` ohne Lifecycle-Test (`DataStoreTest` L54 nur Roundtrip/Clamp).
6. `AGENTS.md:26` — FALSCHE Dokumentation (behauptet ein `@Disabled`-Beispiel in `SpawnGuardListenerTest`; 0 `@Disabled`-Treffer; Plan T15 L106 wiederholt es).
7. `displayNameFallbackMatches` (L78) behauptet den toten Component-Pfad.
8. `setTier(0)`-Ghost ungetestet (`setTierWithinBounds` L68 assertet nie `deadPlayers.isEmpty()`).
9. Penalty-Cooldown 0 Coverage (`PenaltyApplier.java:42` kein Cooldown; Tests feuern nur Einzel-Events).
10. **Systematische Lücken**: `altar/`, `command/` (alle 4), `util/Messages`, `hook/` (alle 3 inkl. Throwable-Isolation), `ReviveManager`, `PenaltyApplier` direkt, Plugin-Lifecycle.

#### Warnungen
1. `api-version` „1.21" (`update.md:11` + plan:10 tragen es als „Entscheidung").
2. `pom.xml:55` — `filtering=true` auf GANZES `src/main/resources` → `${…}`/`@`-Mutationsrisiko. Fix: `<include>plugin.yml</include>`.
3. `AltarManager.java:107` — String-basiertes GUI-Name (gleiche Component-Mismatch, ungetestet).
4. `DataStore.load()` schluckt Fehler (L50 invalider UUID, fehlende World) — kein corrupt-`data.yml`-Test.
5. Phase-1-Config-Validierung ungetestet (`ConfigManager` L59/73/150; Self-Disable-Vertrag plan:93 unverified).
6. NULL tick-basierte Tests (`performTicks` 0 Treffer → Debounce/Auto-Save/Auto-Close/Polling nie ausgeführt).
7. Veraltetes Javadoc `BossDeathListenerTest.java:24` „MockBukkit 3.93.2" vs. pom 4.118.0.

#### Hinweise
1. Irreführende assert-Message `StructureEventListenerTest.java:165` („tier5" vs. tier4).
2. `PluginTestBase` ohne Helpers (kein `performTicks`-Wrapper).
3. Phase-2-Testplan (phase2-plan:180) nur teilweise erfüllt — `ProgressionManagerPhase2Test` fehlt ganz.

#### Stärken
- Isolations-sichere, deterministische Base (mock/unmock pro Test).
- `ConfigManagerPhase2Test` exemplarisch (11 negative-path-Tests).
- `DataStoreTest` Roundtrip + defensive copy.
- `RaidEventListenerTest` verifiziert PDC-Tag.
- `pom.xml` schlank/reproduzierbar.

#### Top-10-Test-Empfehlungen
| # | Test | Abdeckung |
|---|---|---|
| 1 | `ReviveManagerTest` | 1 Item → 0; zweiter Versuch → `NO_ITEM` |
| 2 | `HardmodeListenerTest` (neu) | `FoodLevelChangeEvent` 19→20 → Cap 19; `multiplierForTier` Base-Cache/reset; Golden Apple |
| 3 | `BossDeathListenerTest` Rework | Paper-26-konforme Drop-Assertion |
| 4 | `PermadeathListenerTest` | `clear-inventory:true` + `markDead` |
| 5 | Plugin-Lifecycle | `onDisable` → Task-Liste leer |
| 6 | `setTier(0)` | → `deadPlayers.isEmpty()` |
| 7 | Penalty-Wiederhol-Test | Cooldown-Verhalten bei wiederholtem Interact |
| 8 | `MessagesTest` | Platzhalter/Fallback/Prefix |
| 9 | `AltarManagerTest` | zwei-Spieler-Kollision + echtes Inventar Konsum |
| 10 | `ConfigManagerPhase1Test` | negative Load-Pfade + Disable-Vertrag |

**Dokumentationsfixes (ohne Code)**
- `AGENTS.md:26` + Plan T15 (falsches `@Disabled`-Beispiel)
- `BossDeathListenerTest.java:24` (MockBukkit-Version)
- `plugin.yml` api-version

---

## 5. Testsuite & Build — zusammengefasster Testplan

Aus Reviewer 5 (detailliert in Abschnitt 4.5):

- **Top-10-Testplan** (s. Tabelle in 4.5) deckt alle kritischen Lücken ab: Revive-Konsum auf Production-Pfad, Paper-26-Drop-Semantik, Hardmode-Food-Cap, Permadeath clear-inventory, Plugin-Lifecycle, Ghost-Scenario, Penalty-Cooldown, Messages, Altar-Kollision, Phase-1-Config-Validierung.
- **Dokumentationsfixes ohne Code**: `AGENTS.md:26` + Plan T15; `BossDeathListenerTest.java:24` Version; `plugin.yml` api-version.
- **Build**: `pom.xml` statisch reproduzierbar (fixe Versionen, `release=25`, Paper-Repo); einzige konkrete pom-Risikoquelle ist `filtering=true` auf das komplette `src/main/resources` (`pom.xml:55`).

---

## 6. Empfohlene Reihenfolge der Fixes

1. **Gameplay-Bugs in Abhängigkeitsreihenfolge**
   1. `getDrops()`-Crash (Top-10 #3: `BossDeathListener` + `PermadeathListener` → `dropItemNaturally`)
   2. Revive-Konsum: `consumeOne()`-Array-Kopie + doppelte Konsumption im Altar-Flow (Top-10 #1 + #2 zusammen)
   3. `/reload`-Timer: `onDisable` → `DataStore.shutdown()` (Top-10 #4)
   4. Food-Nerf-Bedingung invertieren (Top-10 #5)
   5. `slotOwners` pro Spieler (Top-10 #6)
   6. Component-Migration zusammen über 3 Dateien: `ReviveItemConfig`, `ReviveItemValidator`, `AltarManager` (Top-10 #7)
2. **Sicherheit/UX**
   - `/altar` Permission-Check (Top-10 #10)
   - Penalty-Cooldown (Top-10 #8)
   - `setTier(0)` Ghosts + `reset()`-Call (Top-10 #9)
   - `handleReload`-Result-Auswertung + atomares Reload (Top-10 #10)
3. **Konformität/Hygiene**
   - hardcodierte Strings
   - deprecated APIs (`PotionEffectTypes`/`Sound`/`Particle`)
   - `api-version`
   - AGENTS.md-Fix
   - pom filtering-`<include>`
4. **Test-Nachbau parallel zu den Fixes** (Top-10-Testliste aus Abschnitt 4.5)

---

## 7. Stärken des Projekts (konsolidiert)

- **Architektur-Invarianten halten**: `DataStore` = Single Source of Truth; nur `ProgressionManager` mutiert das Tier.
- **Vollständige plugin.yml ↔ Code-Sync** für alle 4 Commands.
- **Robustes `Messages`-Design** (Platzhalter, Fallback, Prefix).
- **Exakter Hook-Vertrag** mit `catch Throwable` (inkl. LinkageError-Isolation).
- **Korrekte `onEnable`/`onDisable`-Reihenfolge** (außer Auto-Save-Timer).
- **Test-Base isolations-sicher** (MockBukkit load/unmock pro Test); `ConfigManagerPhase2Test` exemplarisch (11 negative-path-Tests).
- **`pom.xml` schlank und reproduzierbar** (fixe Versionen, `release=25`, Paper-Repo).
- **Defensives `DataStore`-Design** (Clamp, IOException → dirty, defensive copies, `Location.clone`).
- **`onPlayerJoin`-Ghost-Recovery**, `ReviveResult`-Enum + force-Flag, korrekte PDC-Keys.
