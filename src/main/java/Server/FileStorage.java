package Server;

import java.io.*;
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
     * Writes the declared number of bytes to storage using the file ID as its name.
     * Replaces existing contents at that path and leaves the input stream open.
     * A partial file may remain if saving fails.
     *
     * @param fileId the ID used as the storage file name.
     * @param inputStream the stream supplying the file contents.
     * @param fileSize the nonnegative number of bytes to read.
     * @throws IOException if writing fails or the input ends before all bytes are read.
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
     * Opens an input stream for a stored file.
     *
     * @param fileId the ID used as the storage file name.
     * @return an input stream that the caller must close.
     * @throws IOException if the stored file cannot be opened.
     */
    public static InputStream read(long fileId) throws IOException {
        Path filePath = STORAGE_PATH.resolve(String.valueOf(fileId));

        return new FileInputStream(filePath.toFile());
    }

    /**
     *  Deletes a stored file using its file ID.
     *
     *  @param fileId the ID of the file to delete.
     *  @throws IOException if the file is missing or cannot be deleted.
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
        return Files.isRegularFile(STORAGE_PATH.resolve(String.valueOf(fileId)));
    }
}
