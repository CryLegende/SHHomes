<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHHomes

*Homes sur l'île du joueur (remplace EasyHome, reprend ses données)*

<img src="https://img.shields.io/badge/Paper-1.21.11-f2a900?style=for-the-badge" alt="Paper 1.21.11"> <img src="https://img.shields.io/badge/Java-21-e76f00?style=for-the-badge" alt="Java 21"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Homes sur l'île du joueur (remplace EasyHome, reprend ses données)

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`

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

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`) :

- `config.yml`

## Compilation

Java 21 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
