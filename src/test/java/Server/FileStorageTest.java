package Server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageTest {

    @Test
    void saveReadExistsAndDeleteWorkflow() throws Exception {
        long fileId = System.currentTimeMillis();

        byte[] originalData =
                "Hello FileShare!".getBytes(StandardCharsets.UTF_8);

        try {
            // SAVE
            FileStorage.save(
                    fileId,
                    new ByteArrayInputStream(originalData),
                    originalData.length
            );

            // EXISTS
            assertTrue(FileStorage.exists(fileId));

            // READ
            try (InputStream input = FileStorage.read(fileId)) {
                byte[] readData = input.readAllBytes();

                assertArrayEquals(originalData, readData);
            }

            // DELETE
            FileStorage.delete(fileId);

            // VERIFY DELETE
            assertFalse(FileStorage.exists(fileId));

        } finally {
            // Safety cleanup if the test fails halfway through.
            if (FileStorage.exists(fileId)) {
                FileStorage.delete(fileId);
            }
        }
    }

    @Test
    void saveThrowsWhenInputEndsBeforeExpectedSize() {
        long fileId = System.currentTimeMillis();

        byte[] data = "short".getBytes(StandardCharsets.UTF_8);

        try {
            assertThrows(
                    IOException.class,
                    () -> FileStorage.save(
                            fileId,
                            new ByteArrayInputStream(data),
                            data.length + 10
                    )
            );
        } finally {
            if (FileStorage.exists(fileId)) {
                try {
                    FileStorage.delete(fileId);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Test
    void existsReturnsFalseForMissingFile() {
        long fileId = System.currentTimeMillis();

        assertFalse(FileStorage.exists(fileId));
    }

    @Test
    void readThrowsForMissingFile() {
        long fileId = System.currentTimeMillis();

        assertThrows(
                IOException.class,
                () -> FileStorage.read(fileId)
        );
    }
}