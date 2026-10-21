package org.Cry_Legende.sHHomes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Un home, sérialisé tel quel par Gson : mêmes champs et même ordre que les fichiers d'EasyHome.
 */
public final class Home {

    private String world;
    private double x;
    private double y;
    private double z;
    private float pitch;
    private float yaw;
    private String displayName;
    private String id;
    private String material;
    private Long lastUse;

    // Constructeur vide pour Gson
    @SuppressWarnings("unused")
    private Home() {
    }

    public Home(Location location, String displayName, String id, String material) {
        setLocation(location);
        this.displayName = displayName;
        this.id = id;
        this.material = material;
        this.lastUse = 0L;
    }

    /**
     * null si le monde n'est pas chargé.
     */
    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void setLocation(Location location) {
        if (location.getWorld() != null) {
            this.world = location.getWorld().getName();
        }
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public long getLastUse() {
        return lastUse == null ? 0L : lastUse;
    }

    public void setLastUse(long lastUse) {
        this.lastUse = lastUse;
    }
}
