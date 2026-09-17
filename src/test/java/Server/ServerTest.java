package Server;

import Protocol.ServerResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import java.sql.DriverManager;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerTest {

    private Server server;

    private final String testUsername1 =
            "server_test_user_" + System.nanoTime();

    private final String testUsername2 =
            "server_test_user2_" + System.nanoTime();

    private final String testEmail1 =
            testUsername1 + "@test.com";

    private final String testEmail2 =
            testUsername2 + "@test.com";


    @AfterEach
    void cleanup() throws SQLException {
        if (server == null) {
            return;
        }

        try {
            cleanupUser(testUsername1);
        } finally {
            try {
                cleanupUser(testUsername2);
            } finally {
                server.shutdown();
            }
        }
    }


    /*
     * ---------------------------------------------------------
     * Constructor / shutdown
     * ---------------------------------------------------------
     */

    @Test
    void constructorAndShutdown() {
        server = new Server(0);

        assertNotNull(server.getDatabase());

        assertDoesNotThrow(() -> server.shutdown());
    }


    /*
     * ---------------------------------------------------------
     * register()
     * ---------------------------------------------------------
     */

    @Test
    void registerCreatesUser() throws SQLException {
        server = new Server(0);

        User user = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(user);
        assertEquals(testUsername1, user.getUsername());
        assertEquals(testEmail1, user.getEmail());
        assertNotNull(user.getPasswordHash());

        // Password must not be stored as plaintext.
        assertNotEquals(
                "password123",
                user.getPasswordHash()
        );

        // Verify that the user was actually stored.
        User storedUser =
                server.getDatabase().getUser(testUsername1);

        assertNotNull(storedUser);
        assertEquals(user.getId(), storedUser.getId());
        assertEquals(
                testUsername1,
                storedUser.getUsername()
        );
        assertEquals(
                testEmail1,
                storedUser.getEmail()
        );

        // Verify that the stored password hash works.
        assertTrue(
                BCrypt.checkpw(
                        "password123",
                        storedUser.getPasswordHash()
                )
        );
    }


    /*
     * ---------------------------------------------------------
     * login()
     * ---------------------------------------------------------
     */

    @Test
    void loginSucceedsWithCorrectPassword()
            throws SQLException {

        server = new Server(0);

        User registeredUser = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(registeredUser);

        User loggedInUser = server.login(
                testUsername1,
                "password123"
        );

        assertNotNull(loggedInUser);

        assertEquals(
                registeredUser.getId(),
                loggedInUser.getId()
        );

        assertEquals(
                registeredUser.getUsername(),
                loggedInUser.getUsername()
        );
    }

    @Test
    void loginFailsWithWrongPassword()
            throws SQLException {

        server = new Server(0);

        User registeredUser = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(registeredUser);

        User loggedInUser = server.login(
                testUsername1,
                "wrongPassword"
        );

        assertNull(loggedInUser);
    }

    @Test
    void loginFailsForUnknownUser()
            throws SQLException {

        server = new Server(0);

        User loggedInUser = server.login(
                "nonexistent_user_" + System.nanoTime(),
                "password123"
        );

        assertNull(loggedInUser);
    }


    /*
     * ---------------------------------------------------------
     * uploadFile()
     * ---------------------------------------------------------
     */

    @Test
    void uploadFileStoresDatabaseRecordAndPhysicalFile()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        byte[] content =
                "Hello from ServerTest!"
                        .getBytes(StandardCharsets.UTF_8);

        boolean uploaded = server.uploadFile(
                "server_test.txt",
                content.length,
                owner.getId(),
                new ByteArrayInputStream(content)
        );

        assertTrue(uploaded);

        List<FileRecord> files =
                server.getDatabase()
                        .getFilesByOwner(owner.getId());

        assertEquals(1, files.size());

        FileRecord file = files.get(0);

        assertEquals(
                "server_test.txt",
                file.getFileName()
        );

        assertEquals(
                content.length,
                file.getSize()
        );

        assertEquals(
                owner.getId(),
                file.getOwnerId()
        );

        assertTrue(
                FileStorage.exists(file.getId())
        );

        try (InputStream input =
                     FileStorage.read(file.getId())) {

            byte[] storedContent =
                    input.readAllBytes();

            assertArrayEquals(
                    content,
                    storedContent
            );
        }

        FileRecord storedFile =
                server.getDatabase()
                        .getFile(file.getId());

        assertNotNull(storedFile);

        assertEquals(
                file.getId(),
                storedFile.getId()
        );

        assertEquals(
                file.getFileName(),
                storedFile.getFileName()
        );
    }


    /*
     * ---------------------------------------------------------
     * uploadFile() rollback
     * ---------------------------------------------------------
     */

    @Test
    void uploadFileRollsBackDatabaseWhenStorageFails()
            throws SQLException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        byte[] content =
                "Too short"
                        .getBytes(StandardCharsets.UTF_8);

        boolean uploaded;

        try {
            /*
             * The declared file size is larger than the
             * actual InputStream. FileStorage.save()
             * therefore throws IOException.
             *
             * Server.uploadFile() should catch that
             * exception, remove the database record
             * and return false.
             */
            uploaded = server.uploadFile(
                    "failed_upload.txt",
                    content.length + 100,
                    owner.getId(),
                    new ByteArrayInputStream(content)
            );

        } catch (IOException e) {
            fail(
                    "Server.uploadFile() should handle the "
                            + "storage IOException internally",
                    e
            );
            return;
        }

        assertFalse(uploaded);

        List<FileRecord> files =
                server.getDatabase()
                        .getFilesByOwner(owner.getId());

        assertTrue(files.isEmpty());
    }


    /*
     * ---------------------------------------------------------
     * downloadFile()
     * ---------------------------------------------------------
     */

    @Test
    void ownerCanDownloadOwnFile()
            throws SQLException,
            IOException,
            IllegalAccessException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        byte[] content =
                "Download test content"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "download_test.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        List<FileRecord> files =
                server.getDatabase()
                        .getFilesByOwner(owner.getId());

        assertEquals(1, files.size());

        FileRecord file = files.get(0);

        DownloadData download =
                server.downloadFile(
                        owner.getId(),
                        file.getId()
                );

        assertNotNull(download);

        assertEquals(
                file.getId(),
                download.getId()
        );

        assertEquals(
                "download_test.txt",
                download.getFileName()
        );

        assertEquals(
                content.length,
                download.getSize()
        );

        assertEquals(
                owner.getId(),
                download.getOwnerId()
        );

        try (InputStream input =
                     download.getInputStream()) {

            byte[] downloadedContent =
                    input.readAllBytes();

            assertArrayEquals(
                    content,
                    downloadedContent
            );
        }
    }

    @Test
    void downloadFailsForNonexistentFile()
            throws SQLException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        FileNotFoundException exception =
                assertThrows(
                        FileNotFoundException.class,
                        () -> server.downloadFile(
                                owner.getId(),
                                Long.MAX_VALUE
                        )
                );

        assertEquals(
                ServerResponse.FILE_DOES_NOT_EXIST,
                exception.getMessage()
        );
    }

    @Test
    void downloadFailsWithoutAccess()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User otherUser = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(otherUser);

        byte[] content =
                "Private file"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "private.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        List<FileRecord> files =
                server.getDatabase()
                        .getFilesByOwner(owner.getId());

        assertEquals(1, files.size());

        long fileId =
                files.get(0).getId();

        IllegalAccessException exception =
                assertThrows(
                        IllegalAccessException.class,
                        () -> server.downloadFile(
                                otherUser.getId(),
                                fileId
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_ACCESS_TO_FILE,
                exception.getMessage()
        );
    }


    /*
     * ---------------------------------------------------------
     * getUsersWithAccessToFile()
     * ---------------------------------------------------------
     */

    @Test
    void getUsersWithAccessToFileReturnsSharedUsers()
            throws SQLException,
            IOException,
            IllegalAccessException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User sharedUser = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(sharedUser);

        byte[] content =
                "Shared file"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "shared.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        List<FileRecord> files =
                server.getDatabase()
                        .getFilesByOwner(owner.getId());

        assertEquals(1, files.size());

        long fileId =
                files.get(0).getId();

        List<User> initialUsers =
                server.getUsersWithAccessToFile(
                        owner.getId(),
                        fileId
                );

        assertTrue(initialUsers.isEmpty());

        server.shareFile(
                owner.getId(),
                fileId,
                sharedUser.getUsername()
        );

        List<User> users =
                server.getUsersWithAccessToFile(
                        owner.getId(),
                        fileId
                );

        assertEquals(1, users.size());

        assertEquals(
                sharedUser.getId(),
                users.get(0).getId()
        );
    }

    @Test
    void getUsersWithAccessToFileRejectsNonOwner()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User otherUser = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(otherUser);

        byte[] content =
                "Owner only"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "owner_only.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalAccessException exception =
                assertThrows(
                        IllegalAccessException.class,
                        () -> server.getUsersWithAccessToFile(
                                otherUser.getId(),
                                fileId
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_OWN_FILE,
                exception.getMessage()
        );
    }

    @Test
    void getUsersWithAccessToFileFailsForNonexistentFile()
            throws SQLException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        FileNotFoundException exception =
                assertThrows(
                        FileNotFoundException.class,
                        () -> server.getUsersWithAccessToFile(
                                owner.getId(),
                                Long.MAX_VALUE
                        )
                );

        assertEquals(
                ServerResponse.FILE_DOES_NOT_EXIST,
                exception.getMessage()
        );
    }


    /*
     * ---------------------------------------------------------
     * shareFile()
     * ---------------------------------------------------------
     */

    @Test
    void shareAndUnshareFileWorkflow()
            throws SQLException,
            IOException,
            IllegalAccessException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User target = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(target);

        byte[] content =
                "Share workflow"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "share_workflow.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        assertFalse(
                server.getDatabase()
                        .hasSharedAccess(
                                target.getId(),
                                fileId
                        )
        );

        server.shareFile(
                owner.getId(),
                fileId,
                target.getUsername()
        );

        assertTrue(
                server.getDatabase()
                        .hasSharedAccess(
                                target.getId(),
                                fileId
                        )
        );

        assertTrue(
                server.getDatabase()
                        .hasAccess(
                                target.getId(),
                                fileId
                        )
        );

        DownloadData download =
                server.downloadFile(
                        target.getId(),
                        fileId
                );

        assertNotNull(download);

        try (InputStream input =
                     download.getInputStream()) {

            assertArrayEquals(
                    content,
                    input.readAllBytes()
            );
        }

        server.unshareFile(
                owner.getId(),
                fileId,
                target.getUsername()
        );

        assertFalse(
                server.getDatabase()
                        .hasSharedAccess(
                                target.getId(),
                                fileId
                        )
        );

        assertFalse(
                server.getDatabase()
                        .hasAccess(
                                target.getId(),
                                fileId
                        )
        );

        assertThrows(
                IllegalAccessException.class,
                () -> server.downloadFile(
                        target.getId(),
                        fileId
                )
        );
    }

    @Test
    void shareFileFailsWhenFileDoesNotExist()
            throws SQLException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        FileNotFoundException exception =
                assertThrows(
                        FileNotFoundException.class,
                        () -> server.shareFile(
                                owner.getId(),
                                Long.MAX_VALUE,
                                testUsername2
                        )
                );

        assertEquals(
                ServerResponse.FILE_DOES_NOT_EXIST,
                exception.getMessage()
        );
    }

    @Test
    void shareFileFailsWhenUserIsNotOwner()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User otherUser = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(otherUser);

        byte[] content =
                "Owner test"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "owner_test.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalAccessException exception =
                assertThrows(
                        IllegalAccessException.class,
                        () -> server.shareFile(
                                otherUser.getId(),
                                fileId,
                                testUsername1
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_OWN_FILE,
                exception.getMessage()
        );
    }

    @Test
    void shareFileFailsForUnknownUser()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        byte[] content =
                "Share test"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "share_test.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> server.shareFile(
                                owner.getId(),
                                fileId,
                                "nonexistent_user_" + System.nanoTime()
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_FOUND,
                exception.getMessage()
        );
    }

    @Test
    void shareFileFailsWhenSharingToOwner()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        byte[] content =
                "Self share"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "self_share.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> server.shareFile(
                                owner.getId(),
                                fileId,
                                owner.getUsername()
                        )
                );

        assertEquals(
                ServerResponse.CAN_NOT_SHARE_FILE_TO_SELF,
                exception.getMessage()
        );
    }

    @Test
    void shareFileFailsWhenAlreadyShared()
            throws SQLException,
            IOException,
            IllegalAccessException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User target = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(target);

        byte[] content =
                "Already shared"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "already_shared.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        server.shareFile(
                owner.getId(),
                fileId,
                target.getUsername()
        );

        assertTrue(
                server.getDatabase()
                        .hasSharedAccess(
                                target.getId(),
                                fileId
                        )
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> server.shareFile(
                                owner.getId(),
                                fileId,
                                target.getUsername()
                        )
                );

        assertEquals(
                ServerResponse.USER_ALREADY_ACCESS_FILE,
                exception.getMessage()
        );
    }


    /*
     * ---------------------------------------------------------
     * unshareFile()
     * ---------------------------------------------------------
     */

    @Test
    void unshareFileFailsWhenUserIsNotShared()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User target = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(target);

        byte[] content =
                "Unshare test"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "unshare_test.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> server.unshareFile(
                                owner.getId(),
                                fileId,
                                target.getUsername()
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_ACCESS_TO_FILE,
                exception.getMessage()
        );
    }

    @Test
    void unshareFileFailsWhenUserIsNotOwner()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User target = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(target);

        byte[] content =
                "Unshare owner test"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "unshare_owner_test.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalAccessException exception =
                assertThrows(
                        IllegalAccessException.class,
                        () -> server.unshareFile(
                                target.getId(),
                                fileId,
                                owner.getUsername()
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_OWN_FILE,
                exception.getMessage()
        );
    }

    @Test
    void unshareFileFailsForUnknownUser()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        byte[] content =
                "Unknown unshare user"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "unknown_unshare.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> server.unshareFile(
                                owner.getId(),
                                fileId,
                                "nonexistent_user_" + System.nanoTime()
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_FOUND,
                exception.getMessage()
        );
    }


    /*
     * ---------------------------------------------------------
     * deleteFile()
     * ---------------------------------------------------------
     */

    @Test
    void deleteFileRemovesDatabaseRecordAndPhysicalFile()
            throws SQLException,
            IOException,
            IllegalAccessException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        byte[] content =
                "Delete workflow"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "delete_test.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        List<FileRecord> files =
                server.getDatabase()
                        .getFilesByOwner(owner.getId());

        assertEquals(1, files.size());

        long fileId =
                files.get(0).getId();

        assertTrue(
                FileStorage.exists(fileId)
        );

        assertNotNull(
                server.getDatabase()
                        .getFile(fileId)
        );

        server.deleteFile(
                owner.getId(),
                fileId
        );

        assertFalse(
                FileStorage.exists(fileId)
        );

        assertNull(
                server.getDatabase()
                        .getFile(fileId)
        );

        assertTrue(
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .isEmpty()
        );
    }

    @Test
    void deleteFileFailsWhenUserIsNotOwner()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User otherUser = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(otherUser);

        byte[] content =
                "Cannot delete"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "cannot_delete.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        IllegalAccessException exception =
                assertThrows(
                        IllegalAccessException.class,
                        () -> server.deleteFile(
                                otherUser.getId(),
                                fileId
                        )
                );

        assertEquals(
                ServerResponse.USER_NOT_OWN_FILE,
                exception.getMessage()
        );

        // Failed deletion must leave both records intact.
        assertTrue(
                FileStorage.exists(fileId)
        );

        assertNotNull(
                server.getDatabase()
                        .getFile(fileId)
        );
    }

    @Test
    void deleteFileFailsForNonexistentFile()
            throws SQLException, IOException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        assertNotNull(owner);

        FileNotFoundException exception =
                assertThrows(
                        FileNotFoundException.class,
                        () -> server.deleteFile(
                                owner.getId(),
                                Long.MAX_VALUE
                        )
                );

        assertEquals(
                ServerResponse.FILE_DOES_NOT_EXIST,
                exception.getMessage()
        );
    }


    /*
     * ---------------------------------------------------------
     * listFiles()
     * ---------------------------------------------------------
     */

    @Test
    void listFilesShowsOwnedAndSharedFiles()
            throws SQLException, IOException, IllegalAccessException {

        server = new Server(0);

        User owner = server.register(
                testUsername1,
                testEmail1,
                "password123"
        );

        User target = server.register(
                testUsername2,
                testEmail2,
                "password456"
        );

        assertNotNull(owner);
        assertNotNull(target);

        byte[] content =
                "List files test"
                        .getBytes(StandardCharsets.UTF_8);

        assertTrue(
                server.uploadFile(
                        "listed_file.txt",
                        content.length,
                        owner.getId(),
                        new ByteArrayInputStream(content)
                )
        );

        long fileId =
                server.getDatabase()
                        .getFilesByOwner(owner.getId())
                        .get(0)
                        .getId();

        server.shareFile(
                owner.getId(),
                fileId,
                target.getUsername()
        );

        String ownerListing =
                server.listFiles(owner);

        assertTrue(
                ownerListing.contains("Your files")
        );

        assertTrue(
                ownerListing.contains("listed_file.txt")
        );

        assertTrue(
                ownerListing.contains(target.getUsername())
        );

        assertTrue(
                ownerListing.contains("Shared with you")
        );

        String targetListing =
                server.listFiles(target);

        assertTrue(
                targetListing.contains("Your files")
        );

        assertTrue(
                targetListing.contains("Shared with you")
        );

        assertTrue(
                targetListing.contains("listed_file.txt")
        );

        assertTrue(
                targetListing.contains(owner.getUsername())
        );
    }


    /*
     * ---------------------------------------------------------
     * Database cleanup
     * ---------------------------------------------------------
     */
    private void cleanupUser(String username)
            throws SQLException {

        Database database = server.getDatabase();

        int userId = -1;

        // Find user ID
        String findUser =
                "SELECT id FROM users WHERE username = ?";

        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "fileshare_admin",
                "581039");
             PreparedStatement statement =
                     connection.prepareStatement(findUser)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    userId = result.getInt("id");
                }
            }
        }

        // User doesn't exist, so there is nothing to clean up.
        if (userId == -1) {
            return;
        }

        // Delete shares involving this user.
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "fileshare_admin",
                "581039");
             PreparedStatement statement =
                     connection.prepareStatement(
                             "DELETE FROM file_shares WHERE user_id = ?")) {

            statement.setInt(1, userId);
            statement.executeUpdate();
        }

        // Find files owned by this user.
        List<Long> fileIds = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "fileshare_admin",
                "581039");
             PreparedStatement statement =
                     connection.prepareStatement(
                             "SELECT id FROM files WHERE owner_id = ?")) {

            statement.setInt(1, userId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    fileIds.add(result.getLong("id"));
                }
            }
        }

        // Delete shares belonging to the user's files.
        for (long fileId : fileIds) {
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                    "fileshare_admin",
                    "581039");
                 PreparedStatement statement =
                         connection.prepareStatement(
                                 "DELETE FROM file_shares WHERE file_id = ?")) {

                statement.setLong(1, fileId);
                statement.executeUpdate();
            }

            // Delete physical file if it exists.
            if (FileStorage.exists(fileId)) {
                try {
                    FileStorage.delete(fileId);
                } catch (IOException e) {
                    throw new SQLException(
                            "Could not delete test file " + fileId, e);
                }
            }
        }

        // Delete database file records.
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "fileshare_admin",
                "581039");
             PreparedStatement statement =
                     connection.prepareStatement(
                             "DELETE FROM files WHERE owner_id = ?")) {

            statement.setInt(1, userId);
            statement.executeUpdate();
        }

        // Finally delete the user.
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/fileshare?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "fileshare_admin",
                "581039");
             PreparedStatement statement =
                     connection.prepareStatement(
                             "DELETE FROM users WHERE id = ?")) {

            statement.setInt(1, userId);
            statement.executeUpdate();
        }
    }
}