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

## Dépendances

- Obligatoires : `SuperiorSkyblock2`
- Optionnelles : `ViaVersion`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/home` | `/homes` | Menu des homes, /home <id>, /home view <joueur> | `shhomes.home` |
| `/sethome` | — | Place un home à votre position | `shhomes.sethome` |
| `/delhome` | — | Supprime un home | `shhomes.delhome` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shhomes.*` | — | `op` |
| `shhomes.admin.*` | — | `false` |
| `shhomes.home` | — | `false` |
| `shhomes.sethome` | — | `false` |
| `shhomes.delhome` | — | `false` |
| `shhomes.admin.tp` | — | `false` |
| `shhomes.admin.delete` | — | `false` |
| `shhomes.admin.view` | — | `false` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHHomes/
└── <a href="src/main/resources/config.yml">config.yml</a>
    ├── settings, messages, groups, menus        (inchangés)
    └── <a href="src/main/resources/config.yml#L39">dialogues</a>
        ├── <a href="src/main/resources/config.yml#L40">actif</a>                 dialogues activés (false : chat comme avant)
        ├── <a href="src/main/resources/config.yml#L43">min-protocol</a>          version de protocole minimale (771 = 1.21.6)
        ├── <a href="src/main/resources/config.yml#L45">expiration</a>            validité d'une fenêtre, en secondes
        ├── <a href="src/main/resources/config.yml#L46">boutons</a>               textes Valider / Annuler
        ├── <a href="src/main/resources/config.yml#L49">nom</a>                   fenêtre du nom (titre, corps, champ, longueur-max)
        ├── <a href="src/main/resources/config.yml#L57">id</a>                    fenêtre de l'ID
        ├── <a href="src/main/resources/config.yml#L64">suppression</a>           confirmation de suppression (corps, corps-admin, oui, non)
        ├── <a href="src/main/resources/config.yml#L77">erreurs</a>               erreurs affichées en rouge dans la fenêtre
        └── <a href="src/main/resources/config.yml#L85">messages</a>              fenêtre expirée, erreur inattendue
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
