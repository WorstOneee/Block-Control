package br.com.serpens.block.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Shared MySQL/MariaDB storage so a block made on one server applies on all of them.
 * Uses the MySQL driver bundled with Paper and a single connection, only ever touched
 * from the store's writer thread.
 *
 * <p>Every write bumps a one-row revision counter in the same transaction; other servers
 * poll that counter and reload only when it changed.
 */
public final class MysqlBackend implements Backend {

    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_]*");

    private final String url;
    private final Properties props = new Properties();
    private final String blocks;
    private final String meta;
    private Connection connection;

    public MysqlBackend(ConfigurationSection cfg) {
        String host = cfg.getString("host", "localhost");
        int port = cfg.getInt("port", 3306);
        String database = cfg.getString("database", "serpens");
        String params = cfg.getString("parameters", "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + (params.isEmpty() ? "" : "?" + params);
        props.setProperty("user", cfg.getString("username", "root"));
        props.setProperty("password", cfg.getString("password", ""));
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "10000");
        String prefix = cfg.getString("table-prefix", "serpensblock_");
        if (!SAFE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("storage.mysql.table-prefix may only contain letters, digits and _");
        }
        this.blocks = prefix + "blocks";
        this.meta = prefix + "meta";
    }

    private Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // replaced below
                }
            }
            connection = DriverManager.getConnection(url, props);
        }
        return connection;
    }

    /** Creates the tables if needed. */
    public void init() throws SQLException {
        try (Statement st = connection().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + blocks + " ("
                    + "owner CHAR(36) NOT NULL, "
                    + "target CHAR(36) NOT NULL, "
                    + "created_at BIGINT NOT NULL, "
                    + "PRIMARY KEY (owner, target)"
                    + ") DEFAULT CHARSET=ascii");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + meta + " ("
                    + "id TINYINT NOT NULL PRIMARY KEY, "
                    + "revision BIGINT NOT NULL"
                    + ")");
            st.executeUpdate("INSERT IGNORE INTO " + meta + " (id, revision) VALUES (1, 0)");
        }
    }

    /** Bulk insert, used for the one-time blocks.yml import. Returns rows inserted. */
    public int importAll(Map<UUID, Set<UUID>> data) throws SQLException {
        Connection c = connection();
        c.setAutoCommit(false);
        int rows = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO " + blocks + " (owner, target, created_at) VALUES (?, ?, ?)")) {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Set<UUID>> e : data.entrySet()) {
                for (UUID target : e.getValue()) {
                    ps.setString(1, e.getKey().toString());
                    ps.setString(2, target.toString());
                    ps.setLong(3, now);
                    ps.addBatch();
                }
            }
            for (int n : ps.executeBatch()) {
                rows += Math.max(n, 0);
            }
            bump(c);
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
        return rows;
    }

    @Override
    public Map<UUID, Set<UUID>> loadAll() throws SQLException {
        Map<UUID, Set<UUID>> out = new HashMap<>();
        try (Statement st = connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT owner, target FROM " + blocks)) {
            while (rs.next()) {
                UUID owner = YamlBackend.parse(rs.getString(1));
                UUID target = YamlBackend.parse(rs.getString(2));
                if (owner != null && target != null) {
                    out.computeIfAbsent(owner, k -> new HashSet<>()).add(target);
                }
            }
        }
        return out;
    }

    @Override
    public void add(UUID owner, UUID target, Map<UUID, Set<UUID>> snapshot) throws SQLException {
        write("INSERT IGNORE INTO " + blocks + " (owner, target, created_at) VALUES (?, ?, ?)", owner, target, true);
    }

    @Override
    public void remove(UUID owner, UUID target, Map<UUID, Set<UUID>> snapshot) throws SQLException {
        write("DELETE FROM " + blocks + " WHERE owner = ? AND target = ?", owner, target, false);
    }

    private void write(String sql, UUID owner, UUID target, boolean withTime) throws SQLException {
        Connection c = connection();
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            ps.setString(2, target.toString());
            if (withTime) {
                ps.setLong(3, System.currentTimeMillis());
            }
            ps.executeUpdate();
            bump(c);
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    private void bump(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE " + meta + " SET revision = revision + 1 WHERE id = 1");
        }
    }

    @Override
    public long revision() throws SQLException {
        try (Statement st = connection().createStatement();
             ResultSet rs = st.executeQuery("SELECT revision FROM " + meta + " WHERE id = 1")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // shutting down
            }
            connection = null;
        }
    }

    @Override
    public String name() {
        return "MySQL (" + blocks + ")";
    }
}
