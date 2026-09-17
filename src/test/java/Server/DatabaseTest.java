package Server;

import org.junit.jupiter.api.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    private Database database;

    @BeforeEach
    void setUp() {
        database = new Database();
    }

    @Test
    void registerGetAndDeleteUserWorkflow() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        String email = username + "@example.com";
        String passwordHash = "testHash";

        User user = null;

        try {
            // REGISTER
            user = database.register(
                    username,
                    email,
                    passwordHash
            );

            assertNotNull(user);
            assertTrue(user.getId() > 0);
            assertEquals(username, user.getUsername());
            assertEquals(email, user.getEmail());
            assertEquals(passwordHash, user.getPasswordHash());

            // GET BY USERNAME
            User byUsername = database.getUser(username);

            assertNotNull(byUsername);
            assertEquals(user.getId(), byUsername.getId());

            // GET BY ID
            User byId = database.getUser(user.getId());

            assertNotNull(byId);
            assertEquals(username, byId.getUsername());

        } finally {
            // Cleanup.
            if (user != null) {
                deleteUser(user.getId());
            }
        }

        // VERIFY CLEANUP
        assertNull(database.getUser(username));
    }

    @Test
    void fileCrudWorkflow() throws Exception {
        String username = "fileowner_" + System.currentTimeMillis();

        User user = null;
        Long fileId = null;

        try {
            // CREATE USER
            user = database.register(
                    username,
                    username + "@example.com",
                    "hash"
            );

            assertNotNull(user);

            // ADD FILE
            fileId = database.addFile(
                    "test.txt",
                    123,
                    user.getId()
            );

            assertTrue(fileId > 0);

            // GET FILE
            FileRecord file = database.getFile(fileId);

            assertNotNull(file);
            assertEquals(fileId, file.getId());
            assertEquals("test.txt", file.getFileName());
            assertEquals(123, file.getSize());
            assertEquals(user.getId(), file.getOwnerId());

            // GET FILES BY OWNER
            List<FileRecord> files =
                    database.getFilesByOwner(user.getId());

            final long sharedFileId = fileId;

            assertTrue(
                    files.stream()
                            .anyMatch(f -> f.getId() == sharedFileId)
            );

            // DELETE FILE
            database.deleteFile(fileId);
            fileId = null;

            // VERIFY DELETE
            assertNull(database.getFile(file.getId()));

        } finally {
            if (fileId != null) {
                database.deleteFile(fileId);
            }

            if (user != null) {
                deleteUser(user.getId());
            }
        }
    }

    @Test
    void sharingWorkflow() throws Exception {
        String ownerUsername =
                "owner_" + System.currentTimeMillis();

        String recipientUsername =
                "recipient_" + System.currentTimeMillis();

        User owner = null;
        User recipient = null;
        Long fileId = null;

        try {
            // CREATE OWNER
            owner = database.register(
                    ownerUsername,
                    ownerUsername + "@example.com",
                    "hash"
            );

            assertNotNull(owner);

            // CREATE RECIPIENT
            recipient = database.register(
                    recipientUsername,
                    recipientUsername + "@example.com",
                    "hash"
            );

            assertNotNull(recipient);

            // CREATE FILE
            fileId = database.addFile(
                    "shared.txt",
                    100,
                    owner.getId()
            );

            assertTrue(fileId > 0);

            // BEFORE SHARING
            assertFalse(
                    database.hasSharedAccess(
                            recipient.getId(),
                            fileId
                    )
            );

            // SHARE
            database.shareFileToUser(
                    fileId,
                    recipient.getId()
            );

            // VERIFY SHARING
            assertTrue(
                    database.hasSharedAccess(
                            recipient.getId(),
                            fileId
                    )
            );

            assertTrue(
                    database.hasAccess(
                            recipient.getId(),
                            fileId
                    )
            );

            List<User> users =
                    database.getUsersWithFileAccess(fileId);

            assertEquals(1, users.size());
            assertEquals(
                    recipient.getId(),
                    users.get(0).getId()
            );

            List<FileRecord> sharedFiles =
                    database.getSharedFiles(recipient.getId());

            final long sharedFileId = fileId;

            assertTrue(
                    sharedFiles.stream()
                            .anyMatch(f -> f.getId() == sharedFileId)
            );

            // UNSHARE
            database.removeSharedAccessToFile(
                    fileId,
                    recipient.getId()
            );

            // VERIFY UNSHARE
            assertFalse(
                    database.hasSharedAccess(
                            recipient.getId(),
                            fileId
                    )
            );

            assertFalse(
                    database.hasAccess(
                            recipient.getId(),
                            fileId
                    )
            );

        } finally {
            if (fileId != null) {
                database.deleteFile(fileId);
            }

            if (owner != null) {
                deleteUser(owner.getId());
            }

            if (recipient != null) {
                deleteUser(recipient.getId());
            }
        }
    }

    private void deleteUser(int userId) throws SQLException {
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "fileshare_admin",
                "581039");
             var statement = connection.prepareStatement(
                     "DELETE FROM users WHERE id = ?")) {

            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }
}