package Server;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;


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
     * @param inputStream the file data to save.
     * @param fileSize size of data in bytes
     * @throws IOException if the file could not be saved.
     */
    public static void save(long fileId, InputStream inputStream, long fileSize) throws IOException {
        Path filePath = STORAGE_PATH.resolve(String.valueOf(fileId));

        try (OutputStream output = new FileOutputStream(filePath.toFile())) {

            byte[] buffer = new byte[64 * 1024];
            long remaining = fileSize;

            while (remaining > 0) {
                int bytesToRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = inputStream.read(buffer, 0, bytesToRead);

                if (bytesRead == -1) {
                    throw new EOFException("Connection ended before file was completely received");
                }

                output.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
    }

    /**
     *  Reads file data from storage using the file ID.
     *
     *  @param fileId the ID of the file to read.
     *  @return the stored file data.
     *  @throws IOException if the file could not be read.
     */
    public static InputStream read(long fileId) throws IOException {
        Path filePath = STORAGE_PATH.resolve(String.valueOf(fileId));

        return new FileInputStream(filePath.toFile());
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
