package org.Cry_Legende.sHHomes.network;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.Cry_Legende.sHHomes.Home;
import org.Cry_Legende.sHHomes.SHHomes;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Mode réseau : les homes de chaque joueur vivent dans la base partagée, une ligne par home.
 *
 * <ul>
 *   <li>à l'arrivée : une fois le départ confirmé par l'autre serveur, les homes sont lus en base (hors du fil
 *       principal) puis mis en cache ; tant qu'ils ne sont pas là, le plugin refuse les actions sur les homes ;</li>
 *   <li>à chaque modification : seules les lignes changées sont écrites (ajout, mise à jour, suppression), jamais
 *       tout le fichier du joueur, donc aucune mise à jour n'en écrase une autre ;</li>
 *   <li>au départ : la barrière de SHNetwork attend que ces écritures soient validées en base.</li>
 * </ul>
 */
public final class NetworkHomes {

    private static final int WRITE_ATTEMPTS = 3;
    private static final long LOAD_RETRY_MS = 10_000L;

    /** Ce que la base contient déjà pour un joueur, relevé au chargement puis à chaque écriture réussie. */
    private static final class State {
        /** clé de ligne -> empreinte (position, ID, contenu) de ce qui est écrit en base. Fil d'écriture seulement. */
        final Map<String, String> written = new HashMap<>();
    }

    private final SHHomes plugin;
    private final HomeDatabase db;
    private final NetworkLink link;
    private final Duration waitMax;
    private final Gson gson = new Gson();
    private final ScheduledThreadPoolExecutor io;
    private final Map<UUID, List<Home>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    /** Joueurs dont le chargement est lancé et pas encore terminé. */
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    /** Joueurs dont la dernière écriture en base a échoué. */
    private final Set<UUID> failed = ConcurrentHashMap.newKeySet();

    public NetworkHomes(SHHomes plugin, HomeDatabase db, NetworkLink link, Duration waitMax) {
        this.plugin = plugin;
        this.db = db;
        this.link = link;
        this.waitMax = waitMax;
        this.io = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "SHHomes-reseau");
            thread.setDaemon(true);
            return thread;
        });
        this.io.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    /** Branche le mode réseau : sauvegarde au départ de chaque joueur. */
    public void start() {
        link.registerSaveTask(this::flush);
    }

    /** Termine les écritures en cours puis ferme la base. */
    public void stop() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            save(uuid);
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Des écritures de homes n'ont pas pu être terminées à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.close();
    }

    /** Vrai quand les homes du joueur sont chargés depuis la base. */
    public boolean isReady(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /** Homes du joueur ; liste vide (non modifiable) tant qu'ils ne sont pas chargés : voir {@link #isReady}. */
    public List<Home> homes(UUID uuid) {
        List<Home> list = cache.get(uuid);
        return list != null ? list : Collections.emptyList();
    }

    // ------------------------------------------------------------------ arrivée

    /** Lance le chargement des homes d'un joueur qui arrive, une fois sa sauvegarde sur l'autre serveur confirmée. */
    public void beginLoad(UUID uuid) {
        if (!loading.add(uuid)) {
            return;
        }
        link.awaitSaved(uuid, waitMax).whenComplete((saved, error) -> {
            if (error != null || !Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + uuid + " non confirmé par l'autre serveur"
                    + (error != null ? " (" + error + ")" : "") + " : homes relus quand même.");
            }
            submit(() -> load(uuid));
        });
    }

    private void load(UUID uuid) {
        if (plugin.getServer().getPlayer(uuid) == null) {
            loading.remove(uuid);
            states.remove(uuid);
            return;
        }
        List<HomeDatabase.Row> rows;
        try {
            rows = db.load(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Homes de " + uuid + " non lus en base (nouvel essai dans "
                + LOAD_RETRY_MS / 1000 + " s) : " + e.getMessage());
            try {
                io.schedule(() -> load(uuid), LOAD_RETRY_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException stopped) {
                loading.remove(uuid);
            }
            return;
        }
        List<Home> list = new ArrayList<>();
        State state = new State();
        for (HomeDatabase.Row row : rows) {
            try {
                Home home = gson.fromJson(row.data(), Home.class);
                if (home == null) {
                    continue;
                }
                home.setKey(row.key());
                list.add(home);
            } catch (JsonParseException e) {
                // jamais écrasée ni supprimée : cette ligne n'entre pas dans ce que ce serveur connaît
                plugin.getLogger().log(Level.SEVERE, "Home illisible en base, laissé intact : " + uuid + " " + row.key(), e);
            }
        }
        // l'empreinte est celle du home réécrit en JSON, comparable à celle des instantanés pris plus tard
        for (int i = 0; i < list.size(); i++) {
            Home home = list.get(i);
            state.written.put(home.getKey(), signature(i, home.getId(), gson.toJson(home)));
        }
        states.put(uuid, state);
        failed.remove(uuid);
        runMain(() -> {
            if (plugin.getServer().getPlayer(uuid) != null) {
                cache.put(uuid, list);
            } else {
                submit(() -> states.remove(uuid));
            }
            loading.remove(uuid);
        });
    }

    // ------------------------------------------------------------------ écriture

    /** Écrit en base les changements des homes du joueur (instantané pris maintenant, écriture sur le fil à part). */
    public void save(UUID uuid) {
        List<Home> list = cache.get(uuid);
        if (list == null) {
            return;
        }
        List<HomeDatabase.Row> rows = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Home home = list.get(i);
            if (home.getKey() == null) {
                home.setKey(UUID.randomUUID().toString());
            }
            rows.add(new HomeDatabase.Row(home.getKey(), i, home.getId(), gson.toJson(home)));
        }
        submit(() -> sync(uuid, rows));
    }

    /** Joueur parti : dernier instantané écrit, puis cache libéré. */
    public void unload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
        loading.remove(uuid);
        submit(() -> states.remove(uuid));
    }

    private void sync(UUID uuid, List<HomeDatabase.Row> rows) {
        State state = states.computeIfAbsent(uuid, k -> new State());
        Map<String, HomeDatabase.Row> wanted = new LinkedHashMap<>();
        for (HomeDatabase.Row row : rows) {
            wanted.put(row.key(), row);
        }
        List<String> deleted = new ArrayList<>();
        for (String key : state.written.keySet()) {
            if (!wanted.containsKey(key)) {
                deleted.add(key);
            }
        }
        List<HomeDatabase.Row> changed = new ArrayList<>();
        for (HomeDatabase.Row row : rows) {
            if (!signature(row.pos(), row.id(), row.data()).equals(state.written.get(row.key()))) {
                changed.add(row);
            }
        }
        if (deleted.isEmpty() && changed.isEmpty()) {
            return;
        }
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                db.apply(uuid, deleted, changed);
                for (String key : deleted) {
                    state.written.remove(key);
                }
                for (HomeDatabase.Row row : changed) {
                    state.written.put(row.key(), signature(row.pos(), row.id(), row.data()));
                }
                failed.remove(uuid);
                return;
            } catch (SQLException e) {
                if (attempt == WRITE_ATTEMPTS) {
                    failed.add(uuid);
                    StringBuilder lost = new StringBuilder();
                    for (HomeDatabase.Row row : changed) {
                        lost.append(' ').append(row.key()).append('=').append(row.data());
                    }
                    plugin.getLogger().log(Level.SEVERE, "Homes de " + uuid + " non écrits en base (" + e.getMessage()
                        + "), supprimés : " + deleted + ", écrits :" + lost);
                    return;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Tâche de départ inscrite auprès de SHNetwork (fil principal, pendant PlayerQuitEvent) : écrit un dernier
     * instantané si le joueur est encore en cache, puis rend un futur terminé quand toutes les écritures déjà
     * demandées sont validées. Un futur en erreur signale à SHNetwork que la sauvegarde est incomplète.
     */
    private CompletableFuture<Void> flush(Player player) {
        UUID uuid = player.getUniqueId();
        save(uuid);
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            io.execute(() -> {
                if (failed.contains(uuid)) {
                    done.completeExceptionally(new IllegalStateException("homes de " + uuid + " non écrits en base"));
                } else {
                    done.complete(null);
                }
            });
        } catch (RejectedExecutionException stopped) {
            done.complete(null);
        }
        return done;
    }

    // ------------------------------------------------------------------ reprise des fichiers locaux

    /**
     * Reprend en base les homes d'un fichier local. Un joueur qui en a déjà en base n'est pas touché.
     *
     * @return vrai si ses homes ont été écrits ; à appeler hors du fil principal
     */
    public boolean importHomes(UUID uuid, List<Home> homes) throws SQLException {
        List<HomeDatabase.Row> rows = new ArrayList<>(homes.size());
        for (int i = 0; i < homes.size(); i++) {
            Home home = homes.get(i);
            rows.add(new HomeDatabase.Row(UUID.randomUUID().toString(), i, home.getId(), gson.toJson(home)));
        }
        return db.importPlayer(uuid, rows);
    }

    // ------------------------------------------------------------------ outils

    private static String signature(int pos, String id, String data) {
        return pos + "|" + id + "|" + data;
    }

    private void submit(Runnable task) {
        try {
            io.execute(task);
        } catch (RejectedExecutionException stopped) {
            // plugin arrêté : plus rien à écrire
        }
    }

    private void runMain(Runnable task) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}
