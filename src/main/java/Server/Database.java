package Server;

import java.sql.*;

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

    public User register(String username, String email, String passwordHash) {
        String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
        try {
            PreparedStatement ps = dbConnection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();

            ResultSet resultSet = ps.getGeneratedKeys();
            if (resultSet.next()) {
                int id = resultSet.getInt(1);
                return new User(id, username, email, passwordHash);
            }

            return null;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    public User getUser(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try {
            PreparedStatement ps = dbConnection.prepareStatement(sql);
            ps.setString(1, username);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                return new User(
                        resultSet.getInt("id"),
                        resultSet.getString("username"),
                        resultSet.getString("email"),
                        resultSet.getString("password")
                );
            }

            return null;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

}
