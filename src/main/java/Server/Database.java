package Server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    private static final String URL = "jdbc:mysql://localhost:3306/fileshare";
    private static final String USER = "fileshare_admin";
    private static final String PASSWORD = "581039";

    private Connection dbConnection;

    public Database() {
        try {
            dbConnection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connected to database.");
        } catch (SQLException e) {
            throw new RuntimeException("Could not connect to database", e);
        }
    }

    public Connection getConnection() {
        return dbConnection;
    }

    /**
     * Registers a new user in the database.
     *
     * @param username the username of the new user.
     * @param email the email address of the new user.
     * @param passwordHash the hashed password of the new user.
     * @return a User object containing the newly created user's information,
     *         or null if no generated ID was returned.
     * @throws SQLException if the user could not be registered.
     */
    public User register(String username, String email, String passwordHash) throws SQLException {
        String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new User(id, username, email, passwordHash);
                }
            }
            return null;
        }
    }

    /**
     * Retrieves a user from the database by username.
     *
     * @param username the username to search for.
     * @return the corresponding User object, or null if no user was found.
     * @throws SQLException if the database query fails.
     */
    public User getUser(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("password")
                    );
                }
            }

            return null;
        }
    }

    /**
     * Adds a file to the database.
     *
     * @param fileName the name of the file.
     * @param size the size of the file in bytes.
     * @param ownerId the ID of the user who owns the file.
     * @return the ID assigned to the file if it was successfully added,
     *         or -1 if no generated ID was returned.
     * @throws SQLException if the file could not be added.
     */
    public long addFile(String fileName, long size, int ownerId) throws SQLException {
        String sql = "INSERT INTO files (file_name, size, owner_id) VALUES(?, ?, ?)";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, fileName);
            ps.setLong(2, size);
            ps.setInt(3, ownerId);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

            return -1;
        }
    }

    /**
     * Deletes a file from the database.
     *
     * @param fileId the ID of the file to delete.
     * @throws SQLException if the deletion fails.
     */
    public void deleteFile(long fileId) throws SQLException {
        String sql = "DELETE FROM files WHERE id = ?";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setLong(1, fileId);
            ps.executeUpdate();
        }
    }

    /**
     * Retrieves a file from the database by its ID.
     *
     * @param id the ID of the file to retrieve.
     * @return a FileRecord containing the file's metadata, or null if no file
     *         with the specified ID was found.
     * @throws SQLException if the database query fails.
     */
    public FileRecord getFile(long id) throws SQLException {
        String sql = "SELECT * FROM files WHERE id = ?";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FileRecord(
                            rs.getLong("id"),
                            rs.getString("file_name"),
                            rs.getLong("size"),
                            rs.getInt("owner_id"),
                            rs.getTimestamp("uploaded_at").toLocalDateTime()
                    );
                }

                return null;
            }
        }
    }

    /**
     * Retrieves all files owned by a user.
     *
     * @param ownerId the ID of the file owner.
     * @return a list containing the user's files, or an empty list if the user
     *         has no files.
     * @throws SQLException if the database query fails.
     */
    public List<FileRecord> getFilesByOwner(int ownerId) throws SQLException {
        String sql = "SELECT * FROM files WHERE owner_id = ?";

        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setInt(1, ownerId);

            try (ResultSet rs = ps.executeQuery()) {
                List<FileRecord> fileList = new ArrayList<>();

                while (rs.next()) {
                    fileList.add(new FileRecord(
                            rs.getLong("id"),
                            rs.getString("file_name"),
                            rs.getLong("size"),
                            rs.getInt("owner_id"),
                            rs.getTimestamp("uploaded_at").toLocalDateTime()
                    ));
                }

                return fileList;
            }
        }
    }

    /**
     * Retrieves all files shared with a user.
     *
     * @param userId the ID of the user.
     * @return a list containing the files shared with the user, or an empty list
     *         if no files have been shared with the user.
     * @throws SQLException if the database query fails.
     */
    public List<FileRecord> getSharedFiles(int userId) throws SQLException {
        String sql = "SELECT * FROM files JOIN file_shares ON files.id = file_shares.file_id WHERE user_id = ?";

        List<FileRecord> files = new ArrayList<>();
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    files.add(new FileRecord(
                            rs.getLong("id"),
                            rs.getString("file_name"),
                            rs.getLong("size"),
                            rs.getInt("owner_id"),
                            rs.getTimestamp("uploaded_at").toLocalDateTime()
                    ));
                }
            }
        }

        return files;
    }

    /**
     * Shares a file with a user.
     *
     * @param fileId the ID of the file to share.
     * @param userId the ID of the user receiving access.
     * @throws SQLException if query failed.
     */
    public void shareFileToUser(long fileId, int userId) throws SQLException {
        String sql = "INSERT INTO file_shares (file_id, user_id) VALUES(?, ?)";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setLong(1, fileId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Removes a user's access to a shared file.
     *
     * @param fileId the ID of the file.
     * @param userId the ID of the user whose access should be removed.
     * @throws SQLException if the deletion fails.
     */
    public void removeSharedAccessToFile(long fileId, int userId) throws SQLException {
        String sql = "DELETE FROM file_shares WHERE file_id = ? AND user_id = ?";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setLong(1, fileId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * Checks if a user has rights to access a file,
     * either via direct ownership or shared access from another user.
     *
     * @param userId the ID of the user accessing the file.
     * @param fileId the ID of the file to be accessed.
     * @return true if the user owns the file or has shared access, otherwise false.
     * @throws SQLException if a database error occurs.
     */
    public boolean hasAccess(int userId, long fileId) throws SQLException {
        FileRecord fr = getFile(fileId);
        if (fr == null) return false;

        if (fr.getOwnerId() == userId) return true;

        String sql = "SELECT 1 FROM file_shares WHERE file_id = ? AND user_id = ?";
        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            ps.setLong(1, fileId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return true;
            }
        }

        return false;
    }
}
