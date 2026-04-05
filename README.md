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

---

### 3. [Petite] Renommage de la variable `eee` dans `AdviceListenerManager`

**Commit :** `9892bb9c` — `chore: rename variable eee to methodListenerEntry in AdviceListenerManager`

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

**Commit :** `2d428f85` — `chore: remove dead Groovy code dropped since Arthas 3.0`

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

**Commit :** `eafe290a` — `refactor: extract applyTransformers method to remove code duplication in TransformerManager`

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

**Commit :** `0ff237bb` — `refactor: reduce cyclomatic complexity of EnhancerCommand.enhance by extracting sub-methods`

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

**Commit :** `2a3a2a23` — `refactor: extract canLoadSpyAPI, buildGroupLocationFilter, buildInterceptorProcessors, isLazyClassMatch and processMethodNode from Enhancer.transform to reduce its cyclomatic complexity`

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
