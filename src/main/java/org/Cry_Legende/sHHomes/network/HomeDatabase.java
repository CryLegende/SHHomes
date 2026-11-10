package org.Cry_Legende.sHHomes.network;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Homes en MariaDB, partagés par tous les serveurs : une ligne par home (clé stable, position dans la liste,
 * ID, contenu JSON identique à celui des fichiers data/&lt;uuid&gt;.json). Chargée seulement avec SHNetwork
 * (HikariCP et le pilote MariaDB viennent de {@code libraries:} dans plugin.yml).
 *
 * <p>Toutes les méthodes bloquent : à appeler hors du fil principal.</p>
 */
public final class HomeDatabase {

    private static final String TABLE = "shhomes_homes";

    /** Une ligne de la table. */
    public record Row(String key, int pos, String id, String data) {
    }

    private final HikariDataSource pool;

    public HomeDatabase(String url, String user, String password, int timeoutMs) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("SHHomes");
        hikari.setJdbcUrl(url);
        hikari.setUsername(user);
        hikari.setPassword(password);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.addDataSourceProperty("connectTimeout", String.valueOf(timeoutMs));
        hikari.addDataSourceProperty("socketTimeout", String.valueOf(timeoutMs));
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(timeoutMs);
        // le serveur démarre même si la base est absente : le chargement des homes réessaie ensuite
        hikari.setInitializationFailTimeout(-1);
        hikari.setKeepaliveTime(60_000);
        this.pool = new HikariDataSource(hikari);
    }

    /** Crée la table si elle manque. Une table déjà là ne demande aucun droit CREATE à l'utilisateur. */
    public void createTable() throws SQLException {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM " + TABLE + " LIMIT 1")) {
                return;
            } catch (SQLException missing) {
                // la table n'existe pas encore : création ci-dessous
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "uuid CHAR(36) NOT NULL, "
                + "home_key CHAR(36) NOT NULL, "
                + "pos INT NOT NULL, "
                + "home_id VARCHAR(190) NOT NULL, "
                + "data TEXT NOT NULL, "
                + "updated BIGINT NOT NULL, "
                + "PRIMARY KEY (uuid, home_key), "
                + "KEY idx_home_id (uuid, home_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
    }

    /** Homes du joueur, dans l'ordre de son menu. */
    public List<Row> load(UUID uuid) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT home_key, pos, home_id, data FROM " + TABLE
                 + " WHERE uuid = ? ORDER BY pos, home_key")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4)));
                }
            }
        }
        return rows;
    }

    /**
     * Applique en une transaction les homes supprimés et les homes nouveaux ou modifiés d'un joueur.
     * Seules les lignes citées sont touchées : un home que ce serveur ne connaît pas n'est jamais effacé.
     */
    public void apply(UUID uuid, List<String> deletedKeys, List<Row> upserts) throws SQLException {
        try (Connection c = pool.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!deletedKeys.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + TABLE + " WHERE uuid = ? AND home_key = ?")) {
                        for (String key : deletedKeys) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, key);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (!upserts.isEmpty()) {
                    long now = System.currentTimeMillis();
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLE
                        + " (uuid, home_key, pos, home_id, data, updated) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE pos = VALUES(pos), home_id = VALUES(home_id), data = VALUES(data), updated = VALUES(updated)")) {
                        for (Row row : upserts) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, row.key());
                            ps.setInt(3, row.pos());
                            ps.setString(4, row.id());
                            ps.setString(5, row.data());
                            ps.setLong(6, now);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    /**
     * Reprise des homes d'un fichier local. Un joueur qui a déjà des homes en base n'est pas touché.
     *
     * @return vrai si les homes ont été écrits, faux si le joueur en avait déjà en base
     */
    public boolean importPlayer(UUID uuid, List<Row> rows) throws SQLException {
        try (Connection c = pool.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + TABLE + " WHERE uuid = ? FOR UPDATE")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            c.rollback();
                            return false;
                        }
                    }
                }
                long now = System.currentTimeMillis();
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLE
                    + " (uuid, home_key, pos, home_id, data, updated) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (Row row : rows) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, row.key());
                        ps.setInt(3, row.pos());
                        ps.setString(4, row.id());
                        ps.setString(5, row.data());
                        ps.setLong(6, now);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        }
    }

    public void close() {
        pool.close();
    }
}
