package Server;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Stores file data on the server's filesystem.
 */
public class FileStorage {
    private static final Path STORAGE_PATH =
            Path.of(System.getProperty("user.dir"), "Storage");

    static {
        try {
            Files.createDirectories(STORAGE_PATH);
        } catch (IOException e) {
            throw new RuntimeException("Could not create storage directory", e);
        }
    }

    /**
     * Saves file data using the file ID as the filename.
     *
     * @param fileId the ID of the file.
     * @param bytes the file data to save.
     * @throws IOException if the file could not be saved.
     */
    public static void save(long fileId, byte[] bytes) throws IOException {

        Path filePath = STORAGE_PATH.resolve(String.valueOf(fileId));
        try (FileOutputStream fileOut = new FileOutputStream(filePath.toFile())) {
            fileOut.write(bytes);
        }
    }

    /**
     *  Reads file data from storage using the file ID.
     *
     *  @param fileId the ID of the file to read.
     *  @return the stored file data.
     *  @throws IOException if the file could not be read.
     */
    public static byte[] read(long fileId) throws IOException {
        Path filePath = STORAGE_PATH.resolve(String.valueOf(fileId));

        try (FileInputStream fileIn = new FileInputStream(filePath.toFile())) {
            return fileIn.readAllBytes();
        }
    }

    /**
     *  Deletes a stored file using its file ID.
     *
     *  @param fileId the ID of the file to delete.
     *  @throws IOException if the file could not be deleted.
     */
    public static void delete(long fileId) throws IOException {
        Path filePath = STORAGE_PATH.resolve(String.valueOf(fileId));
        Files.delete(filePath);
    }

    /**
     *  Checks whether a file exists in storage.
     *
     *  @param fileId the ID of the file to check.
     *  @return true if the file exists, otherwise false.
     *  */
    public static boolean exists(long fileId) {
        Path filePath = STORAGE_PATH.resolve(String.valueOf(fileId));
        return Files.exists(filePath);
    }
}
