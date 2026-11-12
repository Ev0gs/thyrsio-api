# Thyrsio — Architecture des modules métier

Nov 7, 2026 · @Daimon

## Contexte et objectifs

Thyrsio est un ERP pour négociants en vin : il suit les stocks de matières sèches et de vin en vrac, planifie les mises en bouteille qui consomment ce stock, et gère les codes-barres GS1 (gencods).

Le premier client est une petite maison de négoce dirigée par deux co-gérants. D'autres négociants suivront : l'architecture doit donc éviter tout ce qui est spécifique à ce premier client.

Le socle technique existe déjà : API Spring Boot 4, PostgreSQL et Flyway, authentification Keycloak, documentation Swagger et tests d'intégration avec Testcontainers.

## Réponses du premier client et hypothèses retenues

Les réponses du premier client fixent le périmètre initial ; chaque choix reste paramétrable pour les clients suivants.

| Sujet | Réponse du client | Conséquence sur l'architecture |
| --- | --- | --- |
| Flux général | Achats de matières sèches et de vin, réception, mise en bouteille, chargement | Modules Achats, Stock, Production et **Expéditions** |
| Origine des mises | Pour des commandes clients, mais aussi pour constituer du stock | Module **Commandes clients** ; la production non affectée part en stock |
| Commandes et mises | Une mise peut servir plusieurs commandes | **Affectations** entre lignes de commande et lignes de mise |
| Contenu d'une commande | Plusieurs produits possibles | Commande composée de **lignes** |
| Habillage | Dépend du client | L'**habillage fait partie du produit fini** |
| Mises en bouteille | Prestataires qui viennent avec un camion de mise | Entité **Prestataire de mise** ; le planning dépend de leurs disponibilités |
| Vin en vrac | Acheté puis stocké dans leurs propres cuves | Module **Cuverie** dès la phase 2 |
| Sites | Un chai de stockage (avec une partie cuverie) et un garage ; les produits finis attendent dans l'un ou l'autre avant chargement | Pas d'emplacement pour la matière sèche : le stock se suit **par article et par lot**. Les cuves (contenance, vin contenu) relèvent du module Cuverie. |
| Numéros de lot | Règle propre au client, à venir | **Générateur paramétrable** ; lot porté par la ligne de mise |
| Gencodes | Selon la commande, au choix ; origine encore inconnue | GTIN **facultatifs**, saisis ou générés ; option « étiquetage GS1 requis » par commande |
| Expéditions | En général un chargement par commande, avec de la flexibilité | Un chargement peut regrouper plusieurs commandes, une commande peut partir en plusieurs fois |
| Capsules CRD | Probablement utilisées | Catégorie CRD suivie par lots ; régime fiscal enregistré sur chaque chargement |
| Autres logiciels | Ventes et facturation gérées ailleurs ; origine des commandes inconnue | Saisie des commandes dans l'API, **import** à prévoir si besoin |
| Utilisateurs | 2, les co-gérants | Un seul rôle métier au départ ; des rôles plus fins restent possibles |

## Principes d'architecture

Thyrsio reste une seule application Spring Boot, découpée en modules métier aux frontières strictes : un **monolithe modulaire**.

### Règles entre modules

- Chaque module est un package de premier niveau (`referentiel`, `stock`, `cuverie`, `production`, `gs1`, `achats`, `tracabilite`), avec ses entités, services, controllers et migrations.
- Un module ne lit et n'écrit jamais les tables d'un autre : il appelle son **service public** ou réagit à ses **événements** (par exemple « mise terminée »).
- Ces règles pourront être vérifiées automatiquement par un test Spring Modulith.

### Stratégie multi-clients

On déploie **une instance par client** : sa propre application, sa propre base de données et son propre realm Keycloak. C'est le prolongement direct du code actuel, sans réintroduire la complexité du multi-tenant. L'isolation des données entre clients est totale.

Pour rester réutilisable par d'autres négociants, trois règles s'appliquent dès maintenant :

1. **La configuration est stockée en données, pas en code.** Les catégories d'articles, les formats, les unités et le préfixe GS1 vivent dans des tables, et non dans des `enum` Java : un autre client pourra avoir des catégories différentes.
2. **Aucune information propre à un client dans le code** : nom, adresse, logo, règles de numérotation passent par une table de paramètres.
3. **La configuration Keycloak est versionnée**, comme le fichier de realm actuel, pour créer l'instance d'un nouveau client en quelques minutes.

Si le nombre de clients grandit au point de rendre coûteuse une instance par client, on pourra passer à une instance mutualisée, avec une colonne `tenant_id` et les *Organizations* de Keycloak. Ces trois règles rendent cette migration possible sans tout réécrire.

## Vue d'ensemble des modules

&#91;embedded content: modules métier · 9 modules, le stock au centre\]

Les flèches suivent les flux : les commandes clients déclenchent la production et les chargements ; achats, cuverie, production et expéditions écrivent dans le stock. Le référentiel est lu par tous les modules.

## Module 1 — Référentiel

Le référentiel décrit tout ce qui existe, sans notion de quantité ; tous les autres modules s'y réfèrent.

| Entité | Rôle | Champs principaux |
| --- | --- | --- |
| Article | Tout ce qui se stocke : matières sèches et vin en vrac | code interne, libellé, catégorie, unité de stock, fournisseur principal, référence fournisseur, seuil d'alerte, actif ou archivé |
| Catégorie d'article | Type d'article, **en table** et non en `enum` | code, libellé, unité par défaut, indicateur « suivi par lot », indicateur « réglementé » (CRD) |
| Unité | Unité de mesure | code (`U`, `L`, `HL`, `KG`), libellé, nombre de décimales autorisées |
| Cuvée | Le vin en tant que produit commercial | nom, appellation, couleur, millésime |
| Produit fini | Ce qui sort d'une mise : une cuvée × un format × un conditionnement × un **habillage** | cuvée, contenance, bouteilles par carton, cartons par palette, habillage standard ou dédié à un client, GTIN bouteille et carton facultatifs |
| Client | Importateur, distributeur ou caviste qui passe commande | raison sociale, pays, adresses de livraison, contacts, langue, Incoterm par défaut, n° de TVA intracommunautaire, n° d'accise s'il est entrepositaire agréé, étiquetage GS1 requis par défaut |
| Fournisseur | Vend les matières sèches et le vin | raison sociale, contacts, délai moyen de livraison |
| Prestataire de mise | Vient mettre en bouteille avec son camion | raison sociale, contacts, cadence moyenne (bouteilles par heure), tarif indicatif |

Catégories initiales pour le premier client : bouteille, bouchon, capsule, capsule CRD, étiquette, contre-étiquette, collerette, carton, croisillon, palette, film, vin en vrac.

Le **vin en vrac est un article comme les autres**, avec l'unité litre et un suivi par lot. Le moteur de stock, les lots et la traçabilité fonctionnent ainsi de la même façon pour une capsule et pour du vin. Le module Cuverie ajoute par-dessus la gestion des cuves.

Un **produit fini** est distinct d'un article parce qu'il se fabrique au lieu de se consommer : il n'entre en stock que par une mise en bouteille.

**L'habillage fait partie du produit fini.** Une Cuvée X 2024 avec la contre-étiquette d'un importateur japonais n'est pas interchangeable avec la même bouteille habillée pour la France : ce sont deux produits finis, chacun avec sa nomenclature, son stock et ses gencodes éventuels. Un habillage dédié indique le client auquel il est réservé.

## Module 2 — Stock

Le stock est un **journal de mouvements** : on n'écrase jamais une quantité, on ajoute une entrée ou une sortie, et le stock est la somme des mouvements.

Ce principe donne l'historique complet (qui, quand, pourquoi), la correction d'une erreur par un mouvement inverse, le stock à n'importe quelle date passée et la base de la traçabilité.

| Entité | Rôle |
| --- | --- |
| Lot | Ensemble homogène d'un article : numéro de lot (unique par article), date limite éventuelle, notes |
| Mouvement | article, lot, quantité signée, type, date, auteur, référence de l'origine (réception, mise, chargement…) |
| Solde | Stock actuel par article et par lot, mis à jour dans la même transaction que chaque mouvement |
| Réservation | Quantité bloquée pour une mise planifiée ou une commande client, pas encore consommée ni expédiée |

Types de mouvement : réception, consommation de mise, production de mise, ajustement d'inventaire, transfert, casse ou rebut, retour fournisseur, sortie (expédition ou vente saisie à la main ou importée).

### Trois quantités à ne pas confondre

- **Physique** : ce qui est réellement présent.
- **Réservé** : ce qui est promis à des mises planifiées.
- **Disponible** = physique − réservé. C'est la seule quantité utilisée pour planifier une nouvelle mise.

### Fonctionnalités

- Saisie des réceptions, casses et ajustements.
- Inventaire physique : saisie des quantités comptées, puis création automatique des mouvements d'écart.
- Consultation par article ou par lot, et stock à une date donnée.
- Choix du lot consommé en FIFO par défaut, avec la possibilité d'imposer un lot.

### Points techniques

- **Pas de stock négatif** : verrouillage optimiste (`@Version`) sur le solde pour gérer deux saisies simultanées.
- **Quantités en `BigDecimal`**, avec le nombre de décimales fixé par l'unité (0 pour des capsules, 2 pour des litres).

## Module 3 — Cuverie

La cuverie gère **ses propres cuves** (code, contenance) et ce que chacune contient : un ou plusieurs lots de vin en vrac, avec leur volume. Le stock garde le volume total de chaque lot. Toute opération de cuverie qui change ce total (entrée, perte, soutirage pour une mise) écrit aussi un mouvement de stock, dans la même transaction ; un transfert sans perte ne change que le contenu des cuves.

Ce module ajoute ce que le stock générique ne sait pas faire :

| Élément | Contenu |
| --- | --- |
| Cuve | nom ou numéro, contenance en litres, matériau (inox, béton, bois), état (vide, pleine, en nettoyage, hors service) |
| Réception de vrac | achat de vin entré dans une cuve : crée le lot de vin (cuvée, millésime, fournisseur, volume) et le mouvement d'entrée |
| Transfert | passage d'une cuve à une autre : le contenu des deux cuves change ; seule la perte éventuelle crée un mouvement de stock |
| Contrôle de capacité | refuse un mouvement qui ferait dépasser la contenance d'une cuve |
| Vue des cuves | volume, lot et cuvée contenus, taux de remplissage, derniers mouvements |

Plus tard, selon les besoins : assemblages de plusieurs lots en un nouveau lot, et suivi des analyses (degré, SO2, acidité).

Toutes les entrées et sorties de vin en vrac enregistrent leur volume, leur appellation et leur couleur. Ces informations serviront à la future comptabilité matières sans avoir à reprendre l'historique.

## Module 4 — Production

La production transforme du vin en vrac et des matières sèches en produits finis, au moyen d'ordres de mise exécutés par un prestataire.

### Nomenclature

Pour chaque produit fini, la nomenclature liste les composants consommés. Exemple pour une bouteille de 75 cl en carton de 6 :

| Composant | Quantité | Par | Taux de perte |
| --- | --- | --- | --- |
| Vin en vrac de la cuvée | 0,75 L | bouteille | à définir |
| Bouteille 75 cl | 1 | bouteille | à définir |
| Bouchon | 1 | bouteille | à définir |
| Capsule (ou capsule CRD) | 1 | bouteille | à définir |
| Étiquette et contre-étiquette | 1 + 1 | bouteille | à définir |
| Carton 6 bouteilles | 1 | carton | à définir |
| Croisillon | 1 | carton | à définir |
| Palette | 1 | palette | à définir |

- Le **taux de perte** couvre la casse et les étiquettes mal posées ; sans lui, le stock théorique s'écarte vite du réel.
- La nomenclature est **versionnée** avec une date de début de validité : un changement d'étiquette crée une nouvelle version, et les mises passées gardent la leur.

### Séance de mise, lignes et affectations

Une mise peut servir plusieurs commandes et produire aussi pour le stock ; elle se décompose donc en trois niveaux.

| Élément | Contenu |
| --- | --- |
| Séance de mise | numéro lisible (MISE-2026-0042), prestataire, date et créneau, statut |
| Ligne de mise | un produit fini et sa version de nomenclature, cuve et lot de vin source, quantité prévue puis réalisée, **numéro de lot de mise** |
| Affectation | quantité d'une ligne de mise réservée à une ligne de commande client ; ce qui n'est affecté à aucune commande part en stock |

- Le **numéro de lot** est produit par un **générateur paramétrable** (motif combinant année, séquence, code de cuvée…), configuré par client. La règle du premier client reste à recevoir.
- Si cette règle impose un lot par commande, on crée une ligne de mise par commande ; sinon, une ligne peut servir plusieurs commandes.

### Cycle de vie

&#91;embedded content: cycle de vie d'un ordre de mise · 4 statuts\]

Seule la clôture crée des mouvements de stock ; planifier et annuler ne touchent qu'aux réservations.

- **Planifier** : calcule les besoins de toutes les lignes (nomenclature × quantité × (1 + taux de perte)), les compare au disponible, signale les manquants, puis réserve le stock. Une planification avec manquants reste possible et déclenche une alerte d'approvisionnement.
- **Clôturer** : saisie du réalisé par ligne (bouteilles produites, casse, consommations réelles). L'application crée les mouvements de consommation, fait entrer les produits finis en stock sous leur lot et libère les réservations de matières. Les quantités affectées restent réservées à leurs commandes jusqu'au chargement.
- **Annuler** : libère les réservations et les affectations, sans aucun mouvement de stock.

La comparaison du **prévu et du réalisé** permet d'ajuster les taux de perte avec des chiffres réels.

### Planning

- Vue calendrier des mises, filtrable par prestataire.
- La capacité dépend du prestataire : sa cadence estime la durée d'une mise, et deux mises du même prestataire ne peuvent pas se chevaucher.
- **Besoins agrégés** sur une période : « pour les mises des trois prochaines semaines, il manque 4 000 capsules et 300 cartons ». C'est le lien avec le module Achats.

## Module 5 — Commandes clients

Chez un négociant, la mise en bouteille est surtout déclenchée par des commandes : c'est de la **production à la commande**, complétée par des mises pour le stock.

| Élément | Contenu |
| --- | --- |
| Commande | client, référence du client (son numéro de bon de commande), date, date de chargement souhaitée, Incoterm, adresse de livraison, étiquetage GS1 requis (prérempli selon le client, modifiable) |
| Ligne de commande | produit fini, quantité en bouteilles, quantité affectée, quantité expédiée |
| Affectation | quantité servie **depuis le stock** de produits finis (un lot existant) ou **depuis une ligne de mise** à venir |

Statuts d'une commande : brouillon, confirmée, en préparation, prête, expédiée (partiellement ou totalement), clôturée, ou annulée.

- Une ligne de commande peut être servie par plusieurs affectations, par exemple une partie du stock existant et le reste d'une prochaine mise.
- Si l'étiquetage GS1 est requis, la planification vérifie que les produits concernés ont leurs GTIN.
- Les commandes sont saisies dans l'API ; un import viendra si elles proviennent d'un autre logiciel du client.

## Module 6 — Expéditions

Un chargement fait sortir du stock les produits finis destinés à une ou plusieurs commandes, et conserve tout ce qui accompagne le transport.

- Un chargement peut regrouper des lignes de **plusieurs commandes**, et une commande peut partir en **plusieurs chargements**.
- La validation du chargement crée les mouvements de sortie de stock, lot par lot.

| Rubrique | Champs |
| --- | --- |
| Transport | mode (route, conteneur maritime, autre), transporteur, n° de conteneur (format ISO 6346, avec clé de contrôle vérifiable), n° de plomb, immatriculation, date et heure de chargement, lieu de départ, destination, Incoterm |
| Colisage | palettes (avec leur SSCC si l'étiquetage GS1 est requis), cartons, bouteilles, poids brut et net, volume en litres par appellation, couleur et titre alcoométrique |
| Régime fiscal | en suspension de droits (mouvement couvert par un DAE) ou en droits acquittés (DSA en France, DAES vers un autre pays de l'UE, ou capsules CRD) |
| Documents | n° du DAE (code ARC) ou du DSA ou DAES, référence de la déclaration d'export, lettre de voiture (CMR pour la route internationale), certificats éventuels, copies jointes |

**Périmètre de Thyrsio** : il génère le bon de livraison, la liste de colisage et les étiquettes palettes, et conserve les numéros et copies des documents officiels. Les DAE, DSA et DAES restent saisis dans **GAMMA2**, l'application de la douane ; Thyrsio fournit les volumes nécessaires. Les règles fiscales sont à confirmer avec le client et son transitaire ([douane : évolutions de la circulation des produits soumis à accises](https://www.douane.gouv.fr/fiche/evolutions-venir-dans-les-modalites-de-circulation-des-produits-soumis-accises)).

## Module 7 — Codification GS1

Le module attribue, conserve et imprime les codes-barres GS1 ; le « gencod » du quotidien est le GTIN-13, imprimé en EAN-13.

| Niveau | Code | Usage |
| --- | --- | --- |
| Bouteille | GTIN-13 (EAN-13) | lecture en caisse |
| Carton | GTIN-14 (ITF-14) | logistique et entrepôt |
| Palette | SSCC, 18 chiffres | identifiant unique de chaque palette physique |

### Fonctionnalités

- **Gencodes facultatifs** : un produit fini peut avoir un GTIN bouteille et un GTIN carton, ou aucun.
- **Deux origines possibles** : un GTIN **saisi** (fourni par le client du négociant, qui vend sous sa propre marque) ou **généré** à partir du préfixe GS1 du négociant, s'il en a un.
- **Registre** des codes : chaque GTIN est lié à un seul produit fini, avec son origine, sa date d'attribution et son statut (actif ou retiré).
- **Attribution automatique** du prochain code libre de la plage : préfixe + référence + clé de contrôle (algorithme modulo 10 de GS1) ; vérification de la clé de tout code saisi.
- **Option par commande** « étiquetage GS1 requis » : la planification vérifie la présence des GTIN, et le chargement génère un **SSCC par palette** avec ses étiquettes logistiques.
- **Images** des codes-barres en PNG ou SVG pour les étiquettes.

### Règles à coder

- **Un code n'est jamais réattribué** à un autre produit, même retiré : le registre archive, il ne supprime pas.
- Un changement significatif du produit (contenance, conditionnement) exige un **nouveau GTIN**.
- Le module affiche le nombre de **codes restants** dans la plage du préfixe.

Les règles d'attribution exactes sont fixées par GS1 et doivent être vérifiées dans leur documentation officielle avant de coder ce module.

## Module 8 — Achats

Les achats transforment l'ERP en outil de pilotage : il indique quoi commander, et quand.

- **Commandes fournisseurs** : lignes d'articles, quantités, prix, date de livraison prévue.
- **Réceptions** : saisie de ce qui est réellement arrivé, avec les numéros de lot ; une réception peut être partielle et crée automatiquement les mouvements d'entrée. Une réception de vin en vrac passe par la Cuverie.
- **Suggestions de réapprovisionnement** : pour chaque article, besoins des mises planifiées − disponible − commandes en cours = quantité à commander, en tenant compte du délai de livraison du fournisseur.

## Module 9 — Traçabilité

La traçabilité relie chaque lot de mise aux lots de vin et de matières sèches consommés, puis aux commandes, aux chargements et aux clients ; elle se calcule à partir des mouvements de stock et des chargements.

- **Recherche descendante** : « ce lot de bouchons était défectueux : dans quelles mises a-t-il servi, quels lots de bouteilles, quels conteneurs et quels clients sont concernés ? »
- **Recherche ascendante** : « un client signale un problème sur ce lot de bouteilles : quel vin, quels bouchons, quelles capsules ont été utilisés, et quels autres clients ont reçu ce lot ? »

En cas de rappel de produit, elle permet de cibler les lots concernés au lieu de tout rappeler. La traçabilité des lots est une obligation réglementaire dans l'alimentaire, vin compris.

## Transverse

Ces éléments servent tous les modules.

| Élément | Contenu |
| --- | --- |
| Rôles | Au départ, un rôle `GERANT` donnant tous les droits métier aux deux co-gérants, et `ADMIN` pour l'administration technique. Les rôles fins (responsable de production, magasinier, lecture seule) restent possibles pour de futurs clients, sans changer le code des modules. |
| Audit | Auteur et date de chaque création ou modification, via l'audit de Spring Data JPA alimenté par l'identifiant Keycloak ; historique complet des versions avec Hibernate Envers si nécessaire |
| Paramètres du client | Une table de paramètres : raison sociale, adresse, logo, préfixe GS1, formats de numérotation |
| Numérotation lisible | MISE-2026-0042, REC-2026-0117… générés par des séquences PostgreSQL ; les UUID restent les identifiants techniques |
| Alertes | Stock sous le seuil, manquants sur une mise planifiée, plage GS1 bientôt épuisée, cuve proche de sa capacité |
| Documents PDF | Fiche de mise pour le prestataire, bon de réception, étiquettes |
| Imports et exports | CSV pour la reprise des données initiales (articles, stocks, gencods) et pour échanger avec les logiciels de vente du client |
| Tableau de bord | Mises de la semaine, alertes, état des cuves, consommations du mois |

## Évolutions ultérieures

Ces sujets sont hors du périmètre initial, mais l'architecture conserve dès maintenant les données dont ils auront besoin.

- **Réglementaire** : comptabilité matières, déclaration récapitulative mensuelle (DRM) aux douanes et suivi des capsules CRD, si le client est entrepositaire agréé. Sujet à cadrer avec le client et son expert-comptable.
- **Transmission à GAMMA2** : envoi automatique des DAE et DSA depuis les chargements, plutôt qu'une saisie manuelle dans l'application de la douane.
- **Intégration avec les logiciels de vente et de facturation** du client : import des commandes, export des chargements.
- **Instance mutualisée** : passage d'une instance par client à une instance partagée (colonne `tenant_id`, *Organizations* de Keycloak), si le nombre de clients le justifie.

## Feuille de route

La construction suit l'ordre des dépendances : chaque phase est utilisable seule et sert de base à la suivante.

1. **Référentiel et Stock** *(fait)* : unités, catégories, fournisseurs, articles, **clients**, lots, mouvements, soldes, inventaire.
   - Le client peut déjà suivre ses stocks de matières sèches.
2. **Cuverie et produits finis** *(fait : cuves, lots de vin, réceptions et transferts, stock de produits finis)* : cuves, réceptions et transferts de vrac, cuvées, produits finis **avec habillage**, nomenclatures versionnées.
   - Prérequis : nombre et contenance des cuves.
3. **Commandes clients et mises en bouteille** *(fait : commandes, couverture par le stock et les mises, réservations, planning, clôture)* : commandes et lignes, prestataires, séances et lignes de mise, affectations, numéros de lot, clôture avec le réalisé, planning.
   - C'est la phase qui apporte le plus de valeur.
   - Prérequis : règle de numérotation des lots, prestataires, taux de perte habituels.
4. **Expéditions** *(fait : chargements, sortie du stock au départ, bon de livraison ; reste la liste de colisage et le régime fiscal)* : chargements, colisage, régime fiscal, bon de livraison et liste de colisage.
   - Prérequis : statut fiscal du client et pratiques de son transitaire.
5. **Codification GS1** : GTIN saisis ou générés, registre, SSCC des palettes.
   - Peut être avancée si une commande l'exige rapidement.
6. **Achats et alertes** : commandes fournisseurs, réceptions, suggestions de réapprovisionnement, alertes.
7. **Traçabilité, documents et tableau de bord** : recherches de lots jusqu'au client, PDF, imports et exports.
8. **Plus tard, selon les besoins** : réglementaire (DRM, CRD), transmission à GAMMA2, intégration avec les logiciels de vente, instance mutualisée.

### Reste à faire (point du 12 novembre 2026)

Le flux physique est complet, de l'achat du vin à l'expédition. Ce qui manque, dans l'ordre conseillé :

1. **Paramètres de la société** : raison sociale, adresse, SIRET, TVA intracommunautaire, n° d'accise d'entrepositaire, logo, coordonnées bancaires. Nécessaires au bon de livraison, qui ne porte pas encore l'expéditeur, puis aux factures.
2. **Facturation** : factures à partir des expéditions parties, numérotation continue sans trou, TVA (France, exonération intra-UE ou export avec la mention légale), avoirs, échéances et suivi des paiements, PDF. À cadrer d'abord : le client garde-t-il son logiciel de facturation (on exporterait alors les expéditions) ?
3. **Achats** (phase 6) : commandes fournisseurs, réceptions partielles qui créent les mouvements, suggestions de réapprovisionnement depuis les manquants des mises.
4. **Réglementaire** : DAE et DSA par expédition (GAMMA2), DRM mensuelle, registre de cave. Les données sont déjà là (mouvements, lots, n° d'accise des clients) ; c'est surtout des exports au format de la douane.
5. **Plus tard** : tarifs par client, liste de colisage et documents d'export, fiche de traçabilité d'un lot (phase 7), codification GS1 (phase 5), rôles fins, mise en production (CI, sauvegardes).

Chaque phase suit la méthode déjà en place : migration Flyway, entités, repository, service, DTO, controller, documentation Swagger, puis tests d'intégration.

## Questions ouvertes

Ces réponses sont à obtenir auprès du client avant la phase concernée.

- [ ] **Facturation** : faut-il facturer dans Thyrsio, ou exporter les expéditions vers le logiciel de facturation actuel du client ?

- [ ] **Numéros de lot** : quelle règle, et un lot est-il propre à une commande ou à une production qui peut servir plusieurs commandes ? (avant la phase 3)
- [ ] **Gencodes** : préfixe GS1 du négociant, codes fournis par ses clients, ou les deux ? Gencodes déjà attribués à reprendre ? (avant la phase 5)
- [ ] **Origine des commandes** : sont-elles déjà saisies dans un autre logiciel, qui proposerait un export ou une API ? (avant la phase 3)
- [ ] **Statut fiscal** : le client est-il entrepositaire agréé, et quelle part de ses ventes va en France, dans l'UE et hors UE ? (avant la phase 4)
- [ ] **Transitaire** : qui établit les documents d'export, et sous quelle forme faut-il lui transmettre les informations ? (avant la phase 4)
- [ ] **Prestataires de mise** : combien, avec quelle cadence et quels délais de réservation ? (avant la phase 3)
- [ ] **Cuves** : combien, avec quelles contenances, et les assemblages sont-ils fréquents ? (avant la phase 2)
- [ ] **Taux de perte** : ont-ils des chiffres habituels de casse et de perte par composant ? (avant la phase 3)
- [ ] **Reprise des données** : sous quelle forme sont tenus aujourd'hui les stocks, les articles et les fournisseurs ? (avant la fin de la phase 1)
