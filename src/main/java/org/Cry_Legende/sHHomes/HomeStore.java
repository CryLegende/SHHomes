package org.Cry_Legende.sHHomes;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Homes des joueurs : un fichier JSON par joueur (data/&lt;uuid&gt;.json), format d'EasyHome.
 * Chaque modification est écrite aussitôt (EasyHome n'écrivait qu'à la déconnexion).
 */
public final class HomeStore {

    private final SHHomes plugin;
    private final File dir;
    private final Gson gson = new Gson();
    private final Map<UUID, List<Home>> homes = new ConcurrentHashMap<>();
    // Fichiers illisibles : jamais réécrits pour ne pas effacer les homes du joueur
    private final Set<UUID> broken = ConcurrentHashMap.newKeySet();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "SHHomes-ecriture"));

    public HomeStore(SHHomes plugin, File dir) {
        this.plugin = plugin;
        this.dir = dir;
        dir.mkdirs();
    }

    public List<Home> get(UUID player) {
        return homes.computeIfAbsent(player, this::read);
    }

    public Home find(UUID player, String id) {
        for (Home home : get(player)) {
            if (home.getId().equalsIgnoreCase(id)) {
                return home;
            }
        }
        return null;
    }

    public void save(UUID player) {
        List<Home> list = homes.get(player);
        if (list == null || broken.contains(player)) {
            return;
        }
        String json = list.isEmpty() ? null : gson.toJson(list);
        writer.execute(() -> write(player, json));
    }

    public void unload(UUID player) {
        save(player);
        homes.remove(player);
    }

    public void shutdown() {
        homes.keySet().forEach(this::save);
        writer.shutdown();
        try {
            writer.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Home> read(UUID player) {
        File file = file(player);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try {
            Home[] array = gson.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), Home[].class);
            return array == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(array));
        } catch (IOException | JsonParseException e) {
            broken.add(player);
            plugin.getLogger().log(Level.SEVERE, "Fichier de homes illisible, laissé intact : " + file.getName(), e);
            return new ArrayList<>();
        }
    }

    private void write(UUID player, String json) {
        File file = file(player);
        try {
            if (json == null) {
                Files.deleteIfExists(file.toPath());
                return;
            }
            File tmp = new File(dir, file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), json, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible d'écrire " + file.getName(), e);
        }
    }

    private File file(UUID player) {
        return new File(dir, player + ".json");
    }
}
