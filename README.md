# Rapport partie 2 — Améliorations d'Arthas
**Edouard PIERRE**

Lien vers le dépôt git : https://github.com/EdwardStonePro/arthas

---

## Introduction

Ce rapport présente les améliorations que j'ai effectuées sur le projet **Arthas**, un outil de diagnostic Java open-source développé par Alibaba. Ces modifications font suite à l'audit de qualité réalisé en binôme avec Mathéo Paszkowski (rapport de partie 1). Je me suis concentré sur les packages `core.command`, `core.advisor`, `core.env`, `core.mcp` et `core.distribution`.

---

## Modifications effectuées

### 1. [Petite] Remplacement des nombres magiques dans `ResultConsumerImpl`

**Commit :** [`5cb3ea0c`](https://github.com/EdwardStonePro/arthas/commit/5cb3ea0c) — `refactor: replace magic numbers with named constants in ResultConsumerImpl`

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

**Commit :** [`d68f53c3`](https://github.com/EdwardStonePro/arthas/commit/d68f53c3) — `refactor: rename SpyInterceptors inner classes with descriptive names`

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

---

### 3. [Petite] Renommage de la variable `eee` dans `AdviceListenerManager`

**Commit :** [`9892bb9c`](https://github.com/EdwardStonePro/arthas/commit/9892bb9c) — `chore: rename variable eee to methodListenerEntry in AdviceListenerManager`

#### Situation existante

Dans la classe `AdviceListenerManager` du package `core.advisor`, une variable de boucle était nommée `eee`, sans aucune signification :

```java
for (Entry<String, List<AdviceListener>> eee : adviceListenerManager.map.entrySet()) {
    List<AdviceListener> listeners = eee.getValue();
    ...
    adviceListenerManager.map.put(eee.getKey(), newResult);
}
```

Ce nom ne donne aucune indication sur ce que représente cette entrée. Un développeur qui lit ce code doit déduire du type générique `Entry<String, List<AdviceListener>>` qu'il s'agit d'une association entre un nom de méthode et une liste d'écouteurs alors que c'est une information qui aurait dû être portée par le nom de la variable.

#### Modification apportée

Renommage de `eee` en `methodListenerEntry`, ce qui reflète exactement ce que contient cette entrée : l'association entre une signature de méthode (`String`) et ses `AdviceListener` associés.

#### Amélioration résultante

```java
// Avant
for (Entry<String, List<AdviceListener>> eee : adviceListenerManager.map.entrySet()) {
    List<AdviceListener> listeners = eee.getValue();

// Après
for (Entry<String, List<AdviceListener>> methodListenerEntry : adviceListenerManager.map.entrySet()) {
    List<AdviceListener> listeners = methodListenerEntry.getValue();
```

Le code est désormais lisible sans avoir à inférer le rôle de la variable depuis son type.

---

### 4. [Petite] Suppression du code mort Groovy

**Commit :** [`2d428f85`](https://github.com/EdwardStonePro/arthas/commit/2d428f85) — `chore: remove dead Groovy code dropped since Arthas 3.0`

#### Situation existante

Trois fichiers Java dans le package `core.command` n'étaient plus utilisés depuis 2016 :

- `monitor200/GroovyScriptCommand.java` — commande Groovy abandonnée
- `monitor200/GroovyAdviceListener.java` — listener associé, annoté `@Deprecated`
- `ScriptSupportCommand.java` — interface de support script, référencée uniquement par les deux fichiers ci-dessus

Le commentaire dans `GroovyScriptCommand` indique explicitement la raison de l'abandon :

```java
/**
 * Groovy support has been completed dropped in Arthas 3.0 because of severer memory leak.
 */
```

La seule référence restante dans le projet actif était dans `BuiltinCommandPack.java`, mais commentée :

```java
// commandClassList.add(GroovyScriptCommand.class);
```

Ces fichiers représentaient 290 lignes de code jamais exécutées, non maintenues et potentiellement trompeuses pour un nouveau contributeur qui pourrait croire la fonctionnalité Groovy encore disponible.

#### Modification apportée

Suppression des trois fichiers. Aucune référence active ne restait dans le reste du projet.

#### Amélioration résultante

La base de code est allégée de 290 lignes mortes. Un nouveau développeur ne risque plus de tomber sur ces classes et de tenter de les utiliser ou de les maintenir.

---

### 5. [Moyenne] Suppression de la duplication dans `TransformerManager` par extraction de méthode

**Commit :** [`eafe290a`](https://github.com/EdwardStonePro/arthas/commit/eafe290a) — `refactor: extract applyTransformers method to remove code duplication in TransformerManager`

#### Situation existante

Dans `TransformerManager`, la méthode `transform` de l'objet anonyme `classFileTransformer` répétait trois fois le même bloc de code, une fois pour chaque liste de transformers (`reTransformers`, `watchTransformers`, `traceTransformers`) :

```java
for (ClassFileTransformer classFileTransformer : reTransformers) {
    byte[] transformResult = classFileTransformer.transform(loader, className, classBeingRedefined,
            protectionDomain, classfileBuffer);
    if (transformResult != null) {
        classfileBuffer = transformResult;
    }
}

for (ClassFileTransformer classFileTransformer : watchTransformers) {
    byte[] transformResult = classFileTransformer.transform(loader, className, classBeingRedefined,
            protectionDomain, classfileBuffer);
    if (transformResult != null) {
        classfileBuffer = transformResult;
    }
}

for (ClassFileTransformer classFileTransformer : traceTransformers) {
    // idem...
}
```

La même logique était également présente dans `lazyClassFileTransformer` pour `lazyTransformers`. Au total, 4 boucles quasi-identiques dans la même classe.

#### Modification apportée

Extraction d'une méthode privée statique `applyTransformers` qui encapsule la logique commune :

```java
private static byte[] applyTransformers(List<ClassFileTransformer> transformers, ClassLoader loader,
        String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
        byte[] classfileBuffer) throws IllegalClassFormatException {
    for (ClassFileTransformer transformer : transformers) {
        byte[] transformResult = transformer.transform(loader, className, classBeingRedefined,
                protectionDomain, classfileBuffer);
        if (transformResult != null) {
            classfileBuffer = transformResult;
        }
    }
    return classfileBuffer;
}
```

La méthode `transform` devient alors :

```java
classfileBuffer = applyTransformers(reTransformers, loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
classfileBuffer = applyTransformers(watchTransformers, loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
classfileBuffer = applyTransformers(traceTransformers, loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
return classfileBuffer;
```

#### Amélioration résultante

La méthode `transform` passe de 30 lignes à 5 lignes. Si le comportement des transformers doit évoluer, la modification se fait à un seul endroit dans `applyTransformers` au lieu de 4.

---

### 6. [Moyenne] Réduction de la complexité de `EnhancerCommand.enhance`

**Commit :** [`0ff237bb`](https://github.com/EdwardStonePro/arthas/commit/0ff237bb) — `refactor: reduce cyclomatic complexity of EnhancerCommand.enhance by extracting sub-methods`

#### Situation existante

La méthode `enhance` de `EnhancerCommand` concentrait toute la logique d'instrumentation en un seul bloc de ~100 lignes, mêlant vérification du lock, validation du listener, gestion des erreurs, traitement du cas "aucune classe affectée" et construction de messages d'aide. Ce dernier bloc en particulier contenait plusieurs conditions imbriquées et une longue construction de chaîne :

```java
if (effect.cCnt() == 0 || effect.mCnt() == 0) {
    if (!StringUtils.isEmpty(effect.getOverLimitMsg())) {
        // cas 1 : over limit
    }
    if (this.lazy) {
        // cas 2 : lazy mode
    } else {
        // cas 3 : message d'aide sur 15 lignes
        String smCommand = ...
        String optionsCommand = ...
        // ...
    }
}
```

La complexité cyclomatique de `enhance` était d'environ **14**. Mais au-delà de ce chiffre, la **complexité cognitive** était particulièrement élevée : un développeur devait maintenir simultanément en tête le contexte du lock de session, l'état de l'effect, le mode lazy et la logique de messages, le tout imbriqué dans un seul bloc try/catch.

#### Modification apportée

Extraction de trois méthodes privées :

- `isSkipJDKTrace(listener)` — détecte si le listener est en mode trace JDK à exclure
- `handleNoClassAffected(process, effect)` — gère les trois cas possibles quand aucune classe n'est affectée, retourne `true` si la commande doit s'arrêter
- `buildNoMatchMessage()` — construit le message d'aide listé, séparant la logique de présentation du flux de contrôle

La méthode `enhance` se résume désormais à un enchaînement clair d'étapes :

```java
boolean skipJDKTrace = isSkipJDKTrace(listener);
// ...
if (effect.cCnt() == 0 || effect.mCnt() == 0) {
    if (handleNoClassAffected(process, effect)) {
        return;
    }
}
```

#### Amélioration résultante

La complexité cyclomatique de `enhance` passe de **14 à 11** ce qui est une petite réduction.

Le gain principal est sur la **complexité cognitive**. En effet, chaque méthode extraite a une responsabilité unique et un nom qui décrit son intention. Un développeur qui lit `enhance` n'a plus besoin de parser mentalement les 35 lignes du bloc "no class affected" pour comprendre ce qui se passe,le nom `handleNoClassAffected` suffisant. S'il veut comprendre le détail, il navigue vers la méthode concernée de façon isolée. La méthode `buildNoMatchMessage` va encore plus loin en séparant la construction du message de la logique de flux, rendant chacune des deux indépendamment lisible et modifiable.

---

### 7. [Moyenne] Réduction de la complexité de `Enhancer.transform`

**Commit :** [`2a3a2a23`](https://github.com/EdwardStonePro/arthas/commit/2a3a2a23) — `refactor: extract canLoadSpyAPI, buildGroupLocationFilter, buildInterceptorProcessors, isLazyClassMatch and processMethodNode from Enhancer.transform to reduce its cyclomatic complexity`

#### Situation existante

La méthode `transform` de la classe `Enhancer` concentrait  environ 190 lignes de logique dans un seul bloc try/catch : vérification du classloader, filtrage en mode lazy, construction des intercepteurs, setup des filtres de localisation, et traitement de chaque méthode. Sa complexité cyclomatique était d'environ 30, ce qui en faisait l'une des méthodes les plus complexes du projet.

#### Modification apportée

Extraction de cinq méthodes privées, chacune avec une responsabilité unique :

| Méthode extraite | Responsabilité |
|---|---|
| `canLoadSpyAPI(ClassLoader, String)` | Vérifie que le classloader peut charger `SpyAPI` |
| `isLazyClassMatch(ClassLoader, String)` | Vérifie si une classe chargée en lazy mode doit être instrumentée |
| `buildInterceptorProcessors()` | Construit la liste des intercepteurs selon le mode trace |
| `buildGroupLocationFilter()` | Construit le filtre de localisation pour éviter la double instrumentation |
| `processMethodNode(...)` | Traite une méthode individuelle (trace existante ou nouvelle instrumentation) |

#### Amélioration résultante

La méthode `transform` passe de environ 190 lignes à 55 lignes. Chaque étape est désormais nommée et lisible séquentiellement :

```java
if (!canLoadSpyAPI(inClassLoader, className))  return null;
// ... filtre lazy ...
final List<InterceptorProcessor> interceptorProcessors = buildInterceptorProcessors();
// ... collecte des méthodes ...
GroupLocationFilter groupLocationFilter = buildGroupLocationFilter();
for (MethodNode methodNode : matchedMethods) {
    processMethodNode(methodNode, classNode, interceptorProcessors, groupLocationFilter, inClassLoader, className);
}
```

Comme pour `EnhancerCommand.enhance`, la réduction de complexité cyclomatique est notable (33 → 16 dans `transform`), mais le bénéfice principal reste la **complexité cognitive** : un développeur peut désormais comprendre le flux global de `transform` en quelques secondes, sans avoir à mémoriser des dizaines de lignes de détails imbriqués.

---

### 8. [Moyenne] Suppression du `instanceof` dans `AdviceListenerManager` par ajout de `isActive()` dans l'interface

**Commit :** [`bc8bcf5b`](https://github.com/EdwardStonePro/arthas/commit/bc8bcf5b) — `refactor: add isActive() to AdviceListener to remove instanceof ProcessAware check in AdviceListenerManager`

#### Situation existante

Le bloc statique de `AdviceListenerManager` planifiait un nettoyage périodique des listeners expirés. La boucle interne contenait un enchaînement de vérifications extern à l'objet : un `instanceof`, un cast, un accès au process, un test de nullité, puis un test de statut :

```java
for (AdviceListener listener : listeners) {
    if (listener instanceof ProcessAware) {
        ProcessAware processAware = (ProcessAware) listener;
        Process process = processAware.getProcess();
        if (process == null) {
            continue;
        }
        ExecStatus status = process.status();
        if (!status.equals(ExecStatus.TERMINATED)) {
            newResult.add(listener);
        }
    }
}
```

Ce code viole le principe "Tell Don't Ask". Au lieu de demander à l'objet s'il est actif, il interroge son type, puis son état interne, depuis l'extérieur. De plus, la présence de `instanceof ProcessAware` crée un couplage fort entre `AdviceListenerManager` et l'interface `ProcessAware`, alors que ce gestionnaire n'a pas à connaître les détails d'implémentation de ses listeners.

#### Modification apportée

Ajout d'une méthode `isActive()` dans l'interface `AdviceListener` avec une implémentation par défaut qui retourne `true` :

```java
// AdviceListener.java
default boolean isActive() {
    return true;
}
```

Override dans `AdviceListenerAdapter` (la classe abstraite qui implémente déjà `ProcessAware`) pour y encapsuler la vraie logique :

```java
// AdviceListenerAdapter.java
@Override
public boolean isActive() {
    if (process == null) {
        return false;
    }
    return !process.status().equals(ExecStatus.TERMINATED);
}
```

La boucle dans `AdviceListenerManager` se réduit à une ligne :

```java
for (AdviceListener listener : listeners) {
    if (listener.isActive()) {
        newResult.add(listener);
    }
}
```

Les imports `ProcessAware`, `Process` et `ExecStatus` ont également été supprimés de `AdviceListenerManager`, qui n'a plus besoin de connaître ces types.

#### Amélioration résultante

Le `AdviceListenerManager` ne connaît plus `ProcessAware` ni `ExecStatus`. La logique de décision "ce listener est-il encore actif ?" appartient désormais à l'objet lui-même, à l'endroit où la réponse peut être fournie avec le plus de précision. Si un nouveau type de listener avec une autre condition d'activité est ajouté à l'avenir, il lui suffira d'overrider `isActive()` sans avoir à toucher à `AdviceListenerManager`.

---

### 9. [Moyenne] Ajout de tests pour `Enhancer`

**Commit :** [`cc1e8f03`](https://github.com/EdwardStonePro/arthas/commit/cc1e8f03) — `test: ajout de 3 tests JUnit 5 pour Enhancer couvrant canLoadSpyAPI et buildInterceptorProcessors`

#### Situation existante

La classe `Enhancer` est la composante centrale du package `core.advisor` : elle orchestre l'instrumentation du bytecode de chaque méthode ciblée. Pourtant, seuls deux tests existaient, tous deux écrits en JUnit 4 :

- `test()` — vérifie que transformer une classe deux fois de suite n'injecte pas deux fois les appels SpyAPI
- `testEnhanceWithClassLoaderHash()` — vérifie que le filtrage par hash de classloader fonctionne

Ces tests couvraient un seul scénario de bout en bout, sans isoler les comportements individuels des méthodes extraites lors de la modification 7.

#### Difficulté de mise en place

Tester `Enhancer` s'est avéré significativement plus complexe que prévu. La classe s'appuie sur des outils particuliers : ASM pour la représentation du bytecode, ByteKit (interne Alibaba) pour l'injection d'intercepteurs, et SpyAPI qui doit impérativement être accessible depuis le bootstrap classloader pour que la transformation puisse aboutir.

La principale difficulté était de comprendre comment inspecter le bytecode produit après transformation. Il n'existe pas de documentation publique sur `AsmUtils` (la classe utilitaire de ByteKit), et les méthodes pertinentes, `loadClass`, `toBytes`, `toClassNode`, `findMethods`, `findMethodInsnNode` n'ont été découvertes qu'en lisant le test existant `test()` et en comprenant comment il décomposait le résultat de `transform()`.

Les tests existants ont servi de point de départ indispensable : c'est en les étudiant ligne par ligne que j'ai compris le cycle complet c'est à dire charger le bytecode original avec `AsmUtils.loadClass`, le passer à `transform()`, récupérer le `ClassNode` résultant, puis interroger ses `MethodNode` pour vérifier la présence ou l'absence d'instructions spécifiques de `SpyAPI`.

Par ailleurs, l'infrastructure de test (injection du jar spy dans le bootstrap classloader via `TestHelper.appendSpyJar`, initialisation d'`ArthasBootstrap`) était difficile à reproduire sans s'appuyer sur les patterns déjà en place.

#### Tests ajoutés

Trois nouveaux tests en JUnit 5 (`@org.junit.jupiter.api.Test`) ont été ajoutés, en utilisant les assertions natives JUnit 5 (`assertNull`, `assertNotNull`, `assertTrue`, `assertEquals`) :

**`transformReturnsNullWhenClassLoaderCannotLoadSpyAPI`**

Couvre la méthode extraite `canLoadSpyAPI`. Arthas injecte des appels à `SpyAPI` dans le bytecode, ce qui implique que le classloader de la classe cible doit pouvoir charger `SpyAPI`. Si ce n'est pas le cas, `transform()` doit abandonner et retourner `null`.

Un classloader personnalisé est créé pour lever `ClassNotFoundException` dès qu'on lui demande de charger `SpyAPI`. `transform()` est appelé avec ce classloader et le résultat est vérifié nul.

```java
ClassLoader spyAPIBlockingLoader = new ClassLoader(null) {
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.equals(SpyAPI.class.getName())) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name, resolve);
    }
};
byte[] result = enhancer.transform(spyAPIBlockingLoader, ...);
assertNull(result);
```

**`transformWithTracingAddsAtBeforeInvokeInstructions`**

Couvre la branche `isTracing=true` de `buildInterceptorProcessors`. En mode `trace`, les intercepteurs de suivi d'appels doivent être injectés, ce qui se traduit par la présence d'instructions `atBeforeInvoke` dans le bytecode produit.

```java
Enhancer enhancer = new Enhancer(listener, true, false, classNameMatcher, null, methodNameMatcher);
// ...
assertTrue(AsmUtils.findMethodInsnNode(printMethod, Type.getInternalName(SpyAPI.class), "atBeforeInvoke").size() > 0);
```

**`transformWithoutTracingHasNoAtBeforeInvokeInstructions`**

Couvre la branche `isTracing=false` de `buildInterceptorProcessors`. En mode `watch`, seuls `atEnter`/`atExit` sont injectés. Le test vérifie simultanément que `atEnter` est bien présent (la transformation a eu lieu) et qu'`atBeforeInvoke` est absent (les intercepteurs de trace n'ont pas été ajoutés).

```java
Enhancer enhancer = new Enhancer(listener, false, false, classNameMatcher, null, methodNameMatcher);
// ...
assertTrue(AsmUtils.findMethodInsnNode(printMethod, Type.getInternalName(SpyAPI.class), "atEnter").size() > 0);
assertEquals(0, AsmUtils.findMethodInsnNode(printMethod, Type.getInternalName(SpyAPI.class), "atBeforeInvoke").size());
```

#### Amélioration résultante

Les deux branches de `buildInterceptorProcessors` (tracing et non-tracing) et le garde-fou de `canLoadSpyAPI` sont désormais couverts par des tests isolés. Ces trois comportements étaient auparavant testés implicitement et partiellement. La couverture de `Enhancer` passe ainsi de 2 à 5 tests.

---

### 10. [Grande] Décomposition de la god class `Enhancer` — extraction de `ClassFilter` et `MethodInstrumentor`

**Commits :**
- [`3aedf60b`](https://github.com/EdwardStonePro/arthas/commit/3aedf60b) — `refactor: extraire ClassFilter depuis Enhancer pour séparer le filtrage de l'instrumentation`
- [`f1ae1231`](https://github.com/EdwardStonePro/arthas/commit/f1ae1231) — `refactor: extraire MethodInstrumentor depuis Enhancer pour séparer l'instrumentation du bytecode de l'orchestration`

#### Lien avec les modifications précédentes

Cette modification s'inscrit dans la continuité directe de la modification 7 (réduction de la complexité cyclomatique de `Enhancer.transform`). En modification 7, cinq méthodes avaient été extraites de `transform` pour en réduire la complexité : `canLoadSpyAPI`, `isLazyClassMatch`, `buildInterceptorProcessors`, `buildGroupLocationFilter` et `processMethodNode`. Ces méthodes restaient cependant dans `Enhancer`, simplement comme méthodes privées d'une même classe.

La modification 7 avait amélioré la lisibilité de `transform`. La modification 10 va plus loin en améliorant l'architecture : ces méthodes sont maintenant réparties entre des classes dont la responsabilité est clairement délimitée.

#### Situation existante

Avant ces extractions, `Enhancer` cumulait trois responsabilités distinctes :

1. **Filtrage** — déterminer quelles classes sont éligibles à l'instrumentation (classloader, type de classe, pattern d'exclusion...)
2. **Instrumentation du bytecode** — sélectionner les méthodes, construire la chaîne d'intercepteurs, injecter les points d'interception SpyAPI
3. **Orchestration** — trouver les classes correspondantes dans la JVM, déclencher le retransform, gérer le cache

Avec environ 550 lignes, `Enhancer` était la god class centrale du package `advisor`. Auparavant, toute modification à l'une des trois responsabilités ci-dessus nécessitait de naviguer dans l'ensemble du fichier.

#### Modification apportée

**Extraction de `ClassFilter`** (commit `3aedf60b`)

La classe `ClassFilter` encapsule toute la logique de filtrage des classes :

| Méthode | Rôle |
|---|---|
| `removeNonEligible(Set<Class<?>>)` | Supprime les classes non éligibles du set, retourne la liste des classes filtrées avec leurs raisons |
| `isTargetClassLoader(ClassLoader)` | Vérifie que le classloader correspond au hash cible |
| `isSelfClassLoader(ClassLoader)` | Détecte le classloader d'Arthas lui-même |
| `isExcludedByName(String)` | Vérifie le pattern d'exclusion par nom de classe |

Les méthodes privées `isSelf`, `isUnsafeClass`, `isUnsupportedClass`, `isExclude` sont internalisées dans `ClassFilter`. Dans `Enhancer`, l'appel devient :

```java
// Avant
List<Pair<Class<?>, String>> filtedList = filter(matchingClasses);

// Après
List<Pair<Class<?>, String>> filtedList = classFilter.removeNonEligible(matchingClasses);
```

**Extraction de `MethodInstrumentor`** (commit `f1ae1231`)

La classe `MethodInstrumentor` encapsule toute la logique d'instrumentation du bytecode des méthodes. Elle reçoit à la construction le contexte nécessaire (`isTracing`, `skipJDKTrace`, `listener`, `affect`, `methodNameMatcher`) et expose une unique méthode publique :

```java
public void instrumentMatchingMethods(ClassNode classNode, ClassLoader inClassLoader, String className)
```

En interne, elle met en place les étapes que la modification 7 avait déjà nommées : `buildInterceptorProcessors`, `findMatchingMethods` (anciennement la boucle de filtrage dans `transform`), `buildGroupLocationFilter`, et `processMethodNode` pour chaque méthode. La méthode `isIgnore` et `isAbstract` y sont également internalisées.

Dans `Enhancer.transform`, le bloc de transformation passe de environ 25 lignes à une seule :

```java
// Avant (modification 7 : 5 appels de méthodes + boucles)
final List<InterceptorProcessor> interceptorProcessors = buildInterceptorProcessors();
List<MethodNode> matchedMethods = ...
GroupLocationFilter groupLocationFilter = buildGroupLocationFilter();
for (MethodNode methodNode : matchedMethods) {
    processMethodNode(methodNode, classNode, interceptorProcessors, groupLocationFilter, inClassLoader, className);
}

// Après (modification 10 : délégation complète)
methodInstrumentor.instrumentMatchingMethods(classNode, inClassLoader, className);
```

#### Amélioration résultante

`Enhancer` passe d'environ 550 lignes à ~200 lignes. Sa responsabilité est désormais claire : orchestrer la découverte de classes et le retransform. Elle ne sait plus rien de la façon dont les classes sont filtrées (c'est `ClassFilter`) ni de la façon dont les méthodes sont instrumentées (c'est `MethodInstrumentor` qui fait ce travail).

Le gain au niveau architectural est illustré par les imports supprimés d'`Enhancer` : 20 imports liés à ASM, ByteKit, et SpyInterceptors ont été retirés.

| Classe | Responsabilité unique |
|---|---|
| `Enhancer` | Orchestration : recherche de classes, retransform, cache |
| `ClassFilter` | Filtrage : éligibilité des classes à l'instrumentation |
| `MethodInstrumentor` | Instrumentation : injection des points d'interception SpyAPI dans le bytecode |

---

### 11. [Grande] Création de `ClassPatternCommand`, super classe commune à `MonitorCommand`, `WatchCommand`, `TraceCommand` et `StackCommand`

**Commit :** [`6b0db6ad`](https://github.com/EdwardStonePro/arthas/commit/6b0db6ad) — `refactor: extraire ClassPatternCommand comme super classe commune de MonitorCommand, WatchCommand, TraceCommand et StackCommand`

#### Situation existante

Les quatre commandes `monitor`, `watch`, `trace` et `stack` partagent toutes les mêmes cinq paramètres de base :

| Paramètre | Type | Annotation CLI | Valeur par défaut |
|---|---|---|---|
| `classPattern` | `String` | `@Argument(index=0)` | — |
| `methodPattern` | `String` | `@Argument(index=1)` | — |
| `conditionExpress` | `String` | `@Argument(index=2, required=false)` | — |
| `isRegEx` | `boolean` | `@Option(shortName="E")` | `false` |
| `numberOfLimit` | `int` | `@Option(shortName="n")` | `100` |

Ces cinq champs, leurs setters annotés, leurs getters, et les implémentations de `getClassNameMatcher()`, `getClassNameExcludeMatcher()`, `getMethodNameMatcher()` étaient copiés-collés dans chacune des quatre classes. Toute correction ou évolution de l'un de ces éléments nécessitait de modifier quatre fichiers à la fois.

#### Modification apportée

Création d'une classe abstraite `ClassPatternCommand extends EnhancerCommand` qui centralise : 
- Les 5 champs communs
- Les 5 setters avec leurs annotations `@Argument` / `@Option`
- Les 5 getters
- Les implémentations par défaut de `getClassNameMatcher()`, `getClassNameExcludeMatcher()`, `getMethodNameMatcher()`

Les quatre commandes étendent désormais `ClassPatternCommand` au lieu d'`EnhancerCommand` directement.

Deux particularités ont nécessité des overrides explicites :

**`StackCommand`** — le `method-pattern` est optionnel (`required=false`), permettant de cibler toutes les méthodes d'une classe: 
```java
@Override
@Argument(index = 1, argName = "method-pattern", required = false)
public void setMethodPattern(String methodPattern) {
    super.setMethodPattern(methodPattern);
}
```

**`WatchCommand`** — insère un argument `express` à l'index 2, décalant `condition-express` à l'index 3. L'override reindexe l'argument :
```java
@Override
@Argument(index = 3, argName = "condition-express", required = false)
public void setConditionExpress(String conditionExpress) {
    super.setConditionExpress(conditionExpress);
}
```

**`TraceCommand`** — garde ses overrides de `getClassNameMatcher()` et `getMethodNameMatcher()` pour le mode path tracing (`-p`), qui nécessite une logique de matching composite.

#### Amélioration résultante

310 lignes supprimées pour 110 ajoutées. Chaque commande ne contient plus que ce qui lui est propre. `MonitorCommand` passe de 155 lignes à 71, `StackCommand` de 120 lignes à 50.

Le framework CLI middleware-cli lit les annotations en remontant la hiérarchie de classes, donc les paramètres définis dans `ClassPatternCommand` sont transparents pour les utilisateurs des commandes. Les exceptions (`required=false` dans Stack, index 3 dans Watch) sont gérées par des overrides ciblés qui rendent les différences explicites plutôt que cachées dans du code dupliqué.

---

### 12. [Grande] Suppression du package `env.convert` — consolidation dans `env`

**Commit :** [`561fe2ce`](https://github.com/EdwardStonePro/arthas/commit/561fe2ce) — `refactor: remove env.convert package, move Converter/ConvertiblePair/ConfigurableConversionService to env and consolidate converters into DefaultConversionService`

#### Situation existante

Le package `core.env.convert` contenait 9 fichiers dont la quasi-totalité n'était utilisée qu'à un seul endroit : `DefaultConversionService`. Parmi eux, plusieurs converters ne contenaient qu'une seule ligne de code (`StringToIntegerConverter`, `StringToLongConverter`, `ObjectToStringConverter`) et deux n'étaient même plus utilisés du tout (`ObjectToStringConverter`, qui n'était jamais enregistré dans `addDefaultConverter`).

L'existence du sous-package créait une fragmentation artificielle : les interfaces `Converter`, `ConfigurableConversionService` et la classe `ConvertiblePair` vivaient dans `env.convert` alors qu'elles appartiennent logiquement à `env`, le package qu'elles servent directement.

#### Modification apportée

Le package `env.convert` est supprimé dans son intégralité. Ses éléments sont répartis comme suit :

**Déplacés dans `env` :**
- `Converter` — interface fonctionnelle, utilisée dans tout le package `env`
- `ConvertiblePair` — classe utilitaire de clé, idem
- `ConfigurableConversionService` — interface de configuration du service de conversion

**Inlinés comme lambdas dans `DefaultConversionService.addDefaultConverter()`** (converters triviaux, une ligne chacun) :
- `StringToIntegerConverter` → `(source, targetType) -> Integer.parseInt(source)`
- `StringToLongConverter` → `(source, targetType) -> Long.parseLong(source)`
- `StringToEnumConverter` → `(source, targetType) -> Enum.valueOf(targetType, source)`

**Supprimé sans remplacement :**
- `ObjectToStringConverter` — jamais enregistré dans `addDefaultConverter`, code mort

**Convertis en classes internes privées de `DefaultConversionService`** (logique non triviale qui justifie une classe dédiée) :
- `StringToBooleanConverter` — gère les valeurs `true/on/yes/1` et `false/off/no/0`
- `StringToInetAddressConverter` — gère `UnknownHostException`
- `StringToArrayConverter` — parsing CSV + récursion via `ConversionService`

`DefaultConversionService` est lui-même déplacé dans `env`. Les deux seuls fichiers externes qui importaient depuis `env.convert` (`AbstractPropertyResolver` et `ConfigurablePropertyResolver`) ont leurs imports mis à jour.

#### Discussion sur l'amélioration

Cette modification est sans doute la plus discutable de celles présentées dans ce rapport.

D'un côté, la suppression du sous-package élimine une fragmentation qui paraissait artificielle : 9 fichiers pour une logique concentrée en un seul point d'utilisation est difficile à justifier. Les converters triviaux (une ligne) en particulier n'avaient aucune raison d'exister comme classes séparées, Java 8 permet précisément d'exprimer ce type de logique en lambdas sans perte de lisibilité.

De l'autre, un sous-package `convert` n'est pas en soi une mauvaise pratique. Quelqu'un qui découvre le code pourrait s'attendre à trouver les converters dans un sous-package dédié, et leur absence dans `env` pourrait surprendre. Le fait que `DefaultConversionService` grossisse avec trois classes internes peut aussi être vu comme une forme de god class naissante, ce qui va à l'encontre des principes que ce projet s'est fixés.

Il est également possible que le sous-package `env.convert` ait été prévu pour accueillir de futurs converters auquel cas le supprimer ferme cette porte sans nécessité.

Néanmoins, je défends cette modification principalement sur la base de l'état actuel du code : un sous-package avec autant de classes triviales et de code mort n'apportait pas de valeur démontrable. Mais je reconnais que le gain est modeste, et qu'une personne différente aurait pu faire le choix inverse avec des arguments tout aussi valables.

## Conclusion sur l'UE GL:

Si l'UE GL m'a appris une chose, c'est que je déteste devoir refactoriser des projets qui sont clairement remplis d'erreurs, de pratiques logicelles douteuses et qui ne comportent pas de patterns reconnaissables classiques qui pourraient clairement aider à réduire la complexité des projets en question.

Ainsi, je suppose que GL encourage les élèves à vouloir utiliser de bonnes pratiques dans la conception et la maintenance architecturelle de logiciels. Utiliser des patterns, éventuellement proposer le TDD (test driven development) pourrait nous éviter de nombreux problèmes des semaines, mois voire années plus tard. 

Par exemple, dans le projet Arthas, que je considère de relativement basse qualité dans l'ensemble, utiliser le TDD aurait forcé l'implémentation de tests qui manquent cruellement à ce projet. 

Ce qui m'a le plus frappé au cours de ce travail, c'est à quel point les problèmes de qualité s'accumulent et se renforcent mutuellement. Une god class comme `Enhancer` n'est pas un accident isolé ; elle existe parce que personne n'a appliqué le principe de responsabilité unique dès le départ, et parce que l'absence de tests rendait toute modification risquée, ce qui encourageait à continuer d'accumuler du code dans la même classe plutôt que de la décomposer. La modification 10 (décomposition d'`Enhancer`) n'aurait pas été possible aussi sereinement si la modification 9 (ajout de tests) ne l'avait pas précédée. C'est un cercle vicieux que TDD peut aider à rompre dès le début.

L'autre leçon que je retiens est que la qualité d'un code se mesure autant à sa lisibilité qu'à son fonctionnement. Arthas fonctionne très bien, il est utilisé en production par des milliers de développeurs. Mais comprendre ce qu'il fait nécessitait, avant ces modifications, de naviguer dans des classes de 500 lignes, des variables nommées `eee`, des nombres magiques sans contexte. Le code était correct, mais pas lisible. GL m'a appris que ces deux dimensions sont indépendantes, et que l'une peut masquer l'autre pendant longtemps avant que les coûts de maintenance ne deviennent visibles.

Je nuancerais cependant la conclusion facile qui consiste à dire qu'il "suffit" d'appliquer les bons patterns. Plusieurs modifications de ce rapport l'illustrent : la suppression du package `env.convert` (section 12) est défendable, mais pas évidente. La création de `ClassPatternCommand` (section 11) simplifie quatre classes, mais introduit une hiérarchie d'héritage dont les cas particuliers (`WatchCommand`, `StackCommand`) nécessitent des overrides qui peuvent surprendre. Refactoriser ce n'est pas appliquer mécaniquement des recettes, c'est constamment arbitrer entre des compromis, ce qui demande du jugement ajouté à de l'expérience (expérience dont je ne dispose pas suffisamment).

Cette UE m'a donné des outils concrets, un vocabulaire précis (SRP, Tell Don't Ask, complexité cyclomatique, code mort), et surtout une sensibilité que je n'avais pas auparavant qui est celle de regarder un projet existant et d'identifier immédiatement ce qui en rend la maintenance coûteuse. C'est peut-être la compétence la plus utile à long terme, parce que dans un contexte professionnel, on passe bien plus de temps à travailler sur du code existant qu'à créer des projets de zéro.
