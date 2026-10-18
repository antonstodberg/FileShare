package Server;

/** Stores a user's ID, username, email address and password hash. */
public class User {
    private final int id;
    private final String username;
    private final String email;
    private final String passwordHash;

    public User(int id, String username, String email, String passwordHash) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
}