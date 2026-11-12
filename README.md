<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHHomes

*Homes sur l'île du joueur (remplace EasyHome, reprend ses données)*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Homes sur l'île du joueur (remplace EasyHome, reprend ses données)

Utilisé sur : **Opale**.

### Saisies par dialogue natif (clients 1.21.6 et plus)

Les saisies qui passaient par le chat s'ouvrent dans une fenêtre Minecraft avec les boutons **Valider** et **Annuler** (Échap annule) :

- **Nom du home** (menu des paramètres) : champ de texte prérempli avec le nom actuel, les codes couleurs `&` restent acceptés.
- **ID du home** : champ de texte prérempli ; comme dans le chat, seul le premier mot est gardé, et l'ID ne doit pas déjà être pris.
- **Suppression** (`/delhome`, menu des paramètres, suppression d'un home par le staff avec `/home view`) : fenêtre de confirmation Oui / Non à la place du menu.

Les erreurs (nom vide ou trop long, ID déjà utilisé) s'affichent en rouge dans la fenêtre, qui se rouvre avec le texte déjà saisi. Les mêmes messages de succès, les mêmes droits (`shhomes.admin.delete` pour supprimer le home d'un autre) et les mêmes données JSON qu'avant.

Les clients plus anciens, ou `dialogues.actif: false`, gardent exactement l'ancienne saisie dans le chat (mot `annuler`) et le menu de confirmation. Avec ViaVersion, le plugin lit la version du client (`dialogues.min-protocol`, 771 = 1.21.6) ; sans ViaVersion tous les clients reçoivent le dialogue.

Chaque fenêtre porte un jeton secret à usage unique, valable `dialogues.expiration` secondes : un clic ancien, rejoué, forgé ou venant d'un autre joueur est ignoré, et le home est revérifié au moment d'agir (toujours présent, bon propriétaire).

## Mode réseau

Avec **SHNetwork** installé (et `reseau.actif: true`, valeur par défaut), plusieurs serveurs de jeu partagent les homes :

- les homes sont lus et écrits en MariaDB (table `shhomes_homes`, une ligne par home : clé stable, position, ID, contenu JSON identique à celui des fichiers) au lieu de `data/<uuid>.json` ;
- à l'arrivée d'un joueur, une fois que l'autre serveur a confirmé sa sauvegarde, ses homes sont lus en base hors du fil principal. Tant qu'ils ne sont pas chargés, `/home`, `/sethome`, `/delhome` et le menu répondent par le message `loading` : le joueur ne voit jamais une liste vide qui n'est pas la sienne. Si la base ne répond pas, la lecture est réessayée toutes les 10 secondes ;
- chaque modification n'écrit que les lignes changées (ajout, mise à jour, suppression), jamais tout le fichier du joueur : deux écritures ne s'écrasent pas ;
- au départ du joueur, SHNetwork attend que ces écritures soient validées en base avant de le laisser entrer sur l'autre serveur ;
- un home dont l'île est simplement inconnue du serveur (joueur sans île connue, monde des îles absent) n'est jamais supprimé : le joueur reçoit le message `islandUnknown`. Hors réseau, la règle reste celle d'avant (home supprimé s'il n'est pas sur l'île) ;
- un home situé sur une île hébergée par l'autre serveur se téléporte comme avant : la garde des îles étrangères de SHSkyBridge annule la téléportation et envoie le joueur sur le serveur de l'île, au point du home ;
- `/shhomes import` (permission `shhomes.admin.import`, console comprise) reprend en base les fichiers `data/<uuid>.json` du serveur où on la lance. À faire une seule fois, sur le serveur qui a les homes ; un joueur qui a déjà des homes en base n'est pas touché, la commande peut être relancée.

Sans SHNetwork, ou sans les clés `reseau` dans la configuration, le plugin se comporte comme avant : fichiers JSON locaux, mêmes messages. La table se crée toute seule au démarrage, ou à la main avec `sql/schema.sql`. `/home view <joueur>` ne voit que les joueurs connectés au même serveur.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`
- Optionnelles : `ViaVersion`, `SHNetwork` (mode réseau)

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/home` | `/homes` | Menu des homes, `/home <id>`, `/home view <joueur>` (staff) | `shhomes.home` (`/home view` : `shhomes.admin.view`) |
| `/sethome` | — | Place un home à votre position | `shhomes.sethome` |
| `/delhome` | — | Supprime un home | `shhomes.delhome` |
| `/shhomes reload` | — | Recharge `config.yml` et `messages.yml` (le bloc `reseau` demande un redémarrage) ; un fichier invalide annule le rechargement ; les homes ne sont jamais touchés | `shhomes.admin.reload` |
| `/shhomes import` | — | Mode réseau : reprend en base les fichiers de homes de ce serveur | `shhomes.admin.import` |
| `/shhomes list <joueur>` | — | Liste les homes d'un joueur, connecté ou non (pseudo exact), depuis la console aussi | `shhomes.admin.list` |

`/shhomes` sans argument affiche une ligne d'usage qui ne liste que les sous-commandes permises à l'expéditeur.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shhomes.*` | Toutes les permissions de SHHomes | `op` |
| `shhomes.home`, `shhomes.sethome`, `shhomes.delhome` | Utiliser la commande de même nom | `op` |
| `shhomes.group.vip`, `shhomes.group.vip+`, `shhomes.group.vipelite` | Limites du groupe de même nom dans `groups` (nombre de homes, délai). Un nœud par clé de `groups` | `op` |
| `shhomes.admin` | Toutes les permissions d'administration (parent des suivantes) | `op` |
| `shhomes.admin.view` | `/home view <joueur>` | `false` |
| `shhomes.admin.tp` | Se téléporter au home d'un joueur depuis son menu | `false` |
| `shhomes.admin.delete` | Supprimer le home d'un joueur depuis son menu | `false` |
| `shhomes.admin.import` | `/shhomes import` | `false` |
| `shhomes.admin.reload` | `/shhomes reload` | `false` |
| `shhomes.admin.list` | `/shhomes list <joueur>` | `false` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHHomes/
├── <a href="src/main/resources/messages.yml">messages.yml</a>   messages et sous-section admin
└── <a href="src/main/resources/config.yml">config.yml</a>
    ├── settings, groups, menus
    ├── <a href="src/main/resources/config.yml#L72">dialogues</a>
    │   ├── <a href="src/main/resources/config.yml#L73">actif</a>                 dialogues activés (false : chat comme avant)
    │   ├── <a href="src/main/resources/config.yml#L76">min-protocol</a>          version de protocole minimale (771 = 1.21.6)
    │   ├── <a href="src/main/resources/config.yml#L78">expiration</a>            validité d'une fenêtre, en secondes
    │   ├── <a href="src/main/resources/config.yml#L79">boutons</a>               textes Valider / Annuler
    │   ├── <a href="src/main/resources/config.yml#L82">nom</a>                   fenêtre du nom (titre, corps, champ, longueur-max)
    │   ├── <a href="src/main/resources/config.yml#L90">id</a>                    fenêtre de l'ID
    │   ├── <a href="src/main/resources/config.yml#L97">suppression</a>           confirmation de suppression (corps, corps-admin, oui, non)
    │   ├── <a href="src/main/resources/config.yml#L110">erreurs</a>               erreurs affichées en rouge dans la fenêtre
    │   └── <a href="src/main/resources/config.yml#L118">messages</a>              fenêtre expirée, erreur inattendue
    └── <a href="src/main/resources/config.yml#L347">reseau</a>                 mode réseau (actif, attente-max-secondes, base-de-donnees)
</pre>

La section `dialogues` est complète dans le jar. Le plugin ne réécrit jamais le `config.yml` du serveur : toute clé absente du fichier (la section entière ou une seule ligne) est lue dans le `config.yml` embarqué dans le jar, sans rien y copier, et les fenêtres gardent donc leurs textes (titre, boutons, champ, erreurs). Pour personnaliser un texte ou un réglage, ajoutez la clé (ou toute la section) dans le `config.yml` du serveur : elle l'emporte sur celle du jar.

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
