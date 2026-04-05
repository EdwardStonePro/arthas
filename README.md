# Rapport partie 2 — Améliorations d'Arthas
**Edouard PIERRE**

Lien vers le dépôt git : https://github.com/EdwardStonePro/arthas

---

## Introduction

Ce rapport présente les améliorations que j'ai effectuées sur le projet **Arthas**, un outil de diagnostic Java open-source développé par Alibaba. Ces modifications font suite à l'audit de qualité réalisé en binôme avec Mathéo Paszkowski (rapport de partie 1). Je me suis concentré sur les packages `core.command`, `core.advisor`, `core.env`, `core.mcp` et `core.distribution`.

---

## Modifications effectuées

### 1. [Petite] Remplacement des nombres magiques dans `ResultConsumerImpl`

**Commit :** `5cb3ea0c` — `refactor: replace magic numbers with named constants in ResultConsumerImpl`

#### Situation existante

La classe `ResultConsumerImpl` du package `core.distribution.impl` contenait plusieurs valeurs numériques écrites en dur dans le code, sans aucune explication sur leur signification :

```java
private long pollTimeLimit = 2 * 1000;

if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {

while (!closed
        && sendingResults.size() < resultBatchSizeLimit
        && sendingDelay < 100
        && waitingTime < pollTimeLimit) {
    ResultModel aResult = resultQueue.poll(100, TimeUnit.MILLISECONDS);

return sendingItemCount >= 100;

|| System.currentTimeMillis() - lastAccessTime < 1000;
```

Un développeur lisant ce code ne peut pas savoir, sans lire le contexte entier, ce que représentent ces valeurs : est-ce un timeout en millisecondes ? Un nombre d'éléments ? Un délai ? De plus, les valeurs `100` apparaissent à trois endroits différents avec trois significations différentes (délai d'envoi, intervalle de polling, seuil de flush), ce qui rend le code particulièrement trompeur.

#### Modification apportée

Ajout de six constantes `private static final` en début de classe, chacune nommée selon son rôle :

```java
private static final long POLL_TIME_LIMIT_MS = 2 * 1000;
private static final long LOCK_TIMEOUT_MS = 500;
private static final long MAX_SENDING_DELAY_MS = 100;
private static final long POLL_INTERVAL_MS = 100;
private static final long FLUSH_ITEM_COUNT_THRESHOLD = 100;
private static final long HEALTH_CHECK_TIMEOUT_MS = 1000;
```

Chaque nombre magique a été remplacée par la constante correspondante.

#### Amélioration résultante

Le code est désormais auto-documenté et chaque constante porte un nom qui décrit son rôle. Par exemple, `sendingDelay < MAX_SENDING_DELAY_MS` est immédiatement lisible, alors que `sendingDelay < 100` nécessitait de comprendre le contexte entier pour en saisir le sens. De plus, si l'on souhaite modifier l'une de ces valeurs (par exemple augmenter le timeout de polling), il suffit de changer la constante à un seul endroit, sans risque d'oublier de changer à un endroit du code ou de modifier la mauvaise valeur `100`.

---

### 2. [Petite] Renommage des intercepteurs dans `SpyInterceptors`

**Commit :** `d68f53c3` — `refactor: rename SpyInterceptors inner classes with descriptive names`

#### Situation existante

La classe `SpyInterceptors` du package `core.advisor` contient neuf classes internes nommées avec des suffixes numériques : `SpyInterceptor1`, `SpyInterceptor2`, `SpyInterceptor3`, `SpyTraceInterceptor1`, etc. Ces noms ne donnent aucune information sur le rôle de chaque intercepteur. Pourtant, chacun est annoté avec une annotation `@At*` qui indique précisément le moment d'interception :

```java
public static class SpyInterceptor1 {
    @AtEnter(inline = true)
    public static void atEnter(...) { ... }
}

public static class SpyInterceptor2 {
    @AtExit(inline = true)
    public static void atExit(...) { ... }
}

public static class SpyInterceptor3 {
    @AtExceptionExit(inline = true)
    public static void atExceptionExit(...) { ... }
}
```

Un développeur qui consulte `Enhancer.java` et voit `parse(SpyInterceptor1.class)` est obligé d'aller lire la définition de `SpyInterceptor1` pour comprendre à quoi elle sert. L'information est présente dans l'annotation, mais pas dans le nom de la classe.

#### Modification apportée

Renommage des neuf classes internes selon leur rôle fonctionnel, déduit de leurs annotations :

| Avant | Après |
|---|---|
| `SpyInterceptor1` | `MethodEnterInterceptor` |
| `SpyInterceptor2` | `MethodExitInterceptor` |
| `SpyInterceptor3` | `MethodExceptionInterceptor` |
| `SpyTraceInterceptor1` | `TraceBeforeInvokeInterceptor` |
| `SpyTraceInterceptor2` | `TraceAfterInvokeInterceptor` |
| `SpyTraceInterceptor3` | `TraceInvokeExceptionInterceptor` |
| `SpyTraceExcludeJDKInterceptor1` | `TraceExcludeJDKBeforeInvokeInterceptor` |
| `SpyTraceExcludeJDKInterceptor2` | `TraceExcludeJDKAfterInvokeInterceptor` |
| `SpyTraceExcludeJDKInterceptor3` | `TraceExcludeJDKInvokeExceptionInterceptor` |

Les imports dans `core/advisor/Enhancer.java` ont également été mis à jour. Le fichier `labs/arthas-grpc-web-proxy/.../Enhancer.java` utilise un import wildcard `SpyInterceptors.*` et ne nécessitait pas de modification.

#### Amélioration résultante

Le code de `Enhancer.java` devient lisible sans avoir à naviguer vers `SpyInterceptors` :

```java
// Avant
interceptorProcessors.addAll(defaultInterceptorClassParser.parse(SpyInterceptor1.class));

// Après
interceptorProcessors.addAll(defaultInterceptorClassParser.parse(MethodEnterInterceptor.class));
```

Le rôle de chaque intercepteur est désormais immédiatement compréhensible à la lecture du nom de la classe.
