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
| Mises en bouteille | Prestataires qui viennent avec un camion de mise | Entité **Prestataire de mise** ; le planning dépend de leurs disponibilités, pas d'une ligne interne |
| Vin en vrac | Acheté puis stocké dans leurs propres cuves | Module **Cuverie** dès la phase 2 : cuves, volumes, transferts |
| Sites | Uniquement leurs chais locaux | Un seul site, mais des **emplacements** (zones, cuves) dès le départ |
| Préfixe GS1 | Information inconnue pour l'instant | Module GS1 paramétrable, avec **import** des codes existants |
| Capsules CRD | Probablement utilisées | Catégorie CRD suivie par lots ; données conservées pour la future comptabilité matières |
| Ventes et expéditions | Gérées dans d'autres logiciels | Pas de module Ventes au début ; **imports et exports** prévus |
| Utilisateurs | 2, les co-gérants | Un seul rôle métier au départ ; des rôles plus fins restent possibles pour d'autres clients |

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

&#91;embedded content: modules métier · 7 modules, le stock au centre\]

Les flèches suivent les mouvements de stock : Achats, Cuverie et Production y écrivent, la Traçabilité les relit. Le référentiel est lu par tous les modules.

## Module 1 — Référentiel

Le référentiel décrit tout ce qui existe, sans notion de quantité ; tous les autres modules s'y réfèrent.

| Entité | Rôle | Champs principaux |
| --- | --- | --- |
| Article | Tout ce qui se stocke : matières sèches et vin en vrac | code interne, libellé, catégorie, unité de stock, fournisseur principal, référence fournisseur, seuil d'alerte, actif ou archivé |
| Catégorie d'article | Type d'article, **en table** et non en `enum` | code, libellé, unité par défaut, indicateur « suivi par lot », indicateur « réglementé » (CRD) |
| Unité | Unité de mesure | code (`U`, `L`, `HL`, `KG`), libellé, nombre de décimales autorisées |
| Cuvée | Le vin en tant que produit commercial | nom, appellation, couleur, millésime |
| Produit fini | Ce qui sort d'une mise : une cuvée × un format × un conditionnement | cuvée, contenance, bouteilles par carton, cartons par palette, codes GS1 associés |
| Fournisseur | Vend les matières sèches et le vin | raison sociale, contacts, délai moyen de livraison |
| Prestataire de mise | Vient mettre en bouteille avec son camion | raison sociale, contacts, cadence moyenne (bouteilles par heure), tarif indicatif |

Catégories initiales pour le premier client : bouteille, bouchon, capsule, capsule CRD, étiquette, contre-étiquette, collerette, carton, croisillon, palette, film, vin en vrac.

Le **vin en vrac est un article comme les autres**, avec l'unité litre et un suivi par lot. Le moteur de stock, les lots et la traçabilité fonctionnent ainsi de la même façon pour une capsule et pour du vin. Le module Cuverie ajoute par-dessus la gestion des cuves.

Un **produit fini** est distinct d'un article parce qu'il se fabrique au lieu de se consommer : il n'entre en stock que par une mise en bouteille, et c'est lui qui porte les gencods.

## Module 2 — Stock

Le stock est un **journal de mouvements** : on n'écrase jamais une quantité, on ajoute une entrée ou une sortie, et le stock est la somme des mouvements.

Ce principe donne l'historique complet (qui, quand, pourquoi), la correction d'une erreur par un mouvement inverse, le stock à n'importe quelle date passée et la base de la traçabilité.

| Entité | Rôle |
| --- | --- |
| Emplacement | Lieu de stockage : zone d'un chai, réserve, ou cuve (type `ZONE` ou `CUVE`) |
| Lot | Ensemble homogène d'un article : numéro de lot fournisseur ou interne, date de réception, date limite éventuelle |
| Mouvement | article, lot, emplacement, quantité signée, type, date, auteur, référence de l'origine (réception, mise…) |
| Solde | Stock actuel par article, lot et emplacement, mis à jour dans la même transaction que chaque mouvement |
| Réservation | Quantité bloquée pour une mise planifiée, pas encore consommée |

Types de mouvement : réception, consommation de mise, production de mise, ajustement d'inventaire, transfert, casse ou rebut, retour fournisseur, sortie (expédition ou vente saisie à la main ou importée).

### Trois quantités à ne pas confondre

- **Physique** : ce qui est réellement présent.
- **Réservé** : ce qui est promis à des mises planifiées.
- **Disponible** = physique − réservé. C'est la seule quantité utilisée pour planifier une nouvelle mise.

### Fonctionnalités

- Saisie des réceptions, casses et ajustements.
- Inventaire physique : saisie des quantités comptées, puis création automatique des mouvements d'écart.
- Consultation par article, lot ou emplacement, et stock à une date donnée.
- Choix du lot consommé en FIFO par défaut, avec la possibilité d'imposer un lot.

### Points techniques

- **Pas de stock négatif** : verrouillage optimiste (`@Version`) sur le solde pour gérer deux saisies simultanées.
- **Quantités en `BigDecimal`**, avec le nombre de décimales fixé par l'unité (0 pour des capsules, 2 pour des litres).

## Module 3 — Cuverie

La cuverie suit le vin en vrac cuve par cuve, en s'appuyant sur le moteur de stock : **une cuve est un emplacement de type `CUVE`**, et le vin qu'elle contient est un lot d'article « vin en vrac ».

Ce module ajoute ce que le stock générique ne sait pas faire :

| Élément | Contenu |
| --- | --- |
| Cuve | nom ou numéro, contenance en litres, matériau (inox, béton, bois), état (vide, pleine, en nettoyage, hors service) |
| Réception de vrac | achat de vin entré dans une cuve : crée le lot de vin (cuvée, millésime, fournisseur, volume) et le mouvement d'entrée |
| Transfert | passage d'une cuve à une autre : un mouvement de sortie et un mouvement d'entrée, avec la perte éventuelle |
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

### Ordre de mise

| Champ | Exemple |
| --- | --- |
| Numéro lisible | MISE-2026-0042 |
| Produit fini et version de nomenclature | Cuvée X 2024, 75 cl, carton de 6 |
| Quantité prévue | en bouteilles |
| Cuve et lot de vin source | cuve 4, lot de vrac n°… |
| Prestataire, date et créneau | prestataire Y, 18 novembre, matin |
| Numéro de lot de mise | celui imprimé sur les bouteilles, obligatoire pour la traçabilité |
| Statut | voir le cycle de vie |

### Cycle de vie

&#91;embedded content: cycle de vie d'un ordre de mise · 4 statuts\]

Seule la clôture crée des mouvements de stock ; planifier et annuler ne touchent qu'aux réservations.

- **Planifier** : calcule les besoins (nomenclature × quantité × (1 + taux de perte)), les compare au disponible, signale les manquants, puis réserve le stock. Une planification avec manquants reste possible et déclenche une alerte d'approvisionnement.
- **Clôturer** : saisie du réalisé (bouteilles produites, casse, consommations réelles). L'application crée les mouvements de consommation de chaque composant et l'entrée en stock des produits finis, puis libère les réservations.
- **Annuler** : libère les réservations, sans aucun mouvement de stock.

La comparaison du **prévu et du réalisé** permet d'ajuster les taux de perte avec des chiffres réels.

### Planning

- Vue calendrier des mises, filtrable par prestataire.
- La capacité dépend du prestataire : sa cadence estime la durée d'une mise, et deux mises du même prestataire ne peuvent pas se chevaucher.
- **Besoins agrégés** sur une période : « pour les mises des trois prochaines semaines, il manque 4 000 capsules et 300 cartons ». C'est le lien avec le module Achats.

## Module 5 — Codification GS1

Le module attribue, conserve et imprime les codes-barres GS1 ; le « gencod » du quotidien est le GTIN-13, imprimé en EAN-13.

| Niveau | Code | Usage |
| --- | --- | --- |
| Bouteille | GTIN-13 (EAN-13) | lecture en caisse |
| Carton | GTIN-14 (ITF-14) | logistique et entrepôt |
| Palette | SSCC, 18 chiffres | identifiant unique de chaque palette physique |

### Fonctionnalités

- **Paramétrage** du préfixe entreprise GS1 de chaque client, qui détermine la plage de codes disponibles.
- **Registre** des codes attribués : chaque GTIN est lié à un seul produit fini, avec sa date d'attribution et son statut (actif ou retiré).
- **Attribution automatique** du prochain code libre : préfixe + référence + clé de contrôle calculée selon l'algorithme modulo 10 de GS1.
- **Import** des codes déjà attribués par le client, avec vérification de leur clé de contrôle.
- **Génération des images** de codes-barres en PNG ou SVG pour les étiquettes.
- Plus tard : génération des SSCC et des étiquettes logistiques palette.

### Règles à coder

- **Un code n'est jamais réattribué** à un autre produit, même retiré : le registre archive, il ne supprime pas.
- Un changement significatif du produit (contenance, conditionnement) exige un **nouveau GTIN**.
- Le module affiche le nombre de **codes restants** dans la plage du préfixe.

Les règles d'attribution exactes sont fixées par GS1 et doivent être vérifiées dans leur documentation officielle avant de coder ce module.

## Module 6 — Achats

Les achats transforment l'ERP en outil de pilotage : il indique quoi commander, et quand.

- **Commandes fournisseurs** : lignes d'articles, quantités, prix, date de livraison prévue.
- **Réceptions** : saisie de ce qui est réellement arrivé, avec les numéros de lot ; une réception peut être partielle et crée automatiquement les mouvements d'entrée. Une réception de vin en vrac passe par la Cuverie.
- **Suggestions de réapprovisionnement** : pour chaque article, besoins des mises planifiées − disponible − commandes en cours = quantité à commander, en tenant compte du délai de livraison du fournisseur.

## Module 7 — Traçabilité

La traçabilité relie chaque lot de mise aux lots de vin et de matières sèches consommés ; elle se calcule entièrement à partir des mouvements de stock.

- **Recherche descendante** : « ce lot de bouchons était défectueux : dans quelles mises a-t-il servi, et quels lots de bouteilles sont concernés ? »
- **Recherche ascendante** : « un client signale un problème sur ce lot de bouteilles : quel vin, quels bouchons, quelles capsules ont été utilisés ? »

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
- **Ventes et expéditions** : le client utilise d'autres logiciels. La première étape est un import des sorties de stock ; une intégration par API viendra si ces logiciels le permettent.
- **Instance mutualisée** : passage d'une instance par client à une instance partagée (colonne `tenant_id`, *Organizations* de Keycloak), si le nombre de clients le justifie.

## Feuille de route

La construction suit l'ordre des dépendances : chaque phase est utilisable seule et sert de base à la suivante.

1. **Référentiel et Stock** : articles, catégories, unités, fournisseurs, emplacements, lots, mouvements, soldes, inventaire.
   - Le client peut déjà suivre ses stocks de matières sèches.
   - Prérequis : savoir sous quelle forme reprendre ses données actuelles.
2. **Cuverie et produits finis** : cuves, réceptions et transferts de vrac, cuvées, produits finis, nomenclatures versionnées.
   - Prérequis : nombre et contenance des cuves.
3. **Mises en bouteille** : prestataires, ordres de mise, planification et réservations, clôture avec le réalisé, planning.
   - C'est la phase qui apporte le plus de valeur.
   - Prérequis : prestataires, format des lots de mise, taux de perte habituels.
4. **Codification GS1** : préfixe, registre, attribution, import, images de codes-barres.
   - Indépendante des phases 2 et 3 : elle peut être avancée si le client en a besoin rapidement.
   - Prérequis : le préfixe GS1 du client.
5. **Achats et alertes** : commandes, réceptions, suggestions de réapprovisionnement, alertes.
6. **Traçabilité, documents et tableau de bord** : recherches de lots, PDF, imports et exports.
7. **Plus tard, selon les besoins** : réglementaire (DRM, CRD), intégration avec les logiciels de vente, instance mutualisée.

Chaque phase suit la méthode déjà en place : migration Flyway, entités, repository, service, DTO, controller, documentation Swagger, puis tests d'intégration.

## Questions ouvertes

Ces réponses sont à obtenir auprès du client avant la phase concernée.

- [ ] **GS1** : l'entreprise a-t-elle un préfixe GS1, de quelle taille, et des gencods déjà attribués à reprendre ? (avant la phase 4)
- [ ] **CRD et douanes** : l'entreprise est-elle entrepositaire agréée, comment déclare-t-elle sa DRM aujourd'hui, et qui la réalise ? (avant toute partie réglementaire)
- [ ] **Logiciels existants** : lesquels pour la vente et la facturation, et proposent-ils des exports CSV ou une API ? (avant les imports et exports)
- [ ] **Prestataires de mise** : combien, avec quelle cadence et quels délais de réservation ? (avant la phase 3)
- [ ] **Cuves** : combien, avec quelles contenances, et les assemblages sont-ils fréquents ? (avant la phase 2)
- [ ] **Numéros de lot** : quel format utilisent-ils aujourd'hui pour les lots de mise ? (avant la phase 3)
- [ ] **Taux de perte** : ont-ils des chiffres habituels de casse et de perte par composant ? (avant la phase 3)
- [ ] **Reprise des données** : sous quelle forme sont tenus aujourd'hui les stocks, les articles et les fournisseurs ? (avant la phase 1)
