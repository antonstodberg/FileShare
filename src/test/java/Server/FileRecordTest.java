package Server;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class FileRecordTest {

    @Test
    void constructorAndGettersReturnCorrectValues() {
        LocalDateTime uploadedAt = LocalDateTime.of(2026, 9, 17, 12, 30);

        FileRecord file = new FileRecord(
                123,
                "test.txt",
                500,
                42,
                uploadedAt
        );

        assertEquals(123, file.getId());
        assertEquals("test.txt", file.getFileName());
        assertEquals(500, file.getSize());
        assertEquals(42, file.getOwnerId());
        assertEquals(uploadedAt, file.getUploadedAt());
    }
}