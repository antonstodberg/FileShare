package Server;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DownloadDataTest {

    @Test
    void gettersReturnValuesFromFileRecord() {
        InputStream inputStream =
                new ByteArrayInputStream("hello".getBytes());

        LocalDateTime uploadedAt = LocalDateTime.of(2026, 9, 17, 12, 30);

        FileRecord file = new FileRecord(
                10,
                "hello.txt",
                5,
                42,
                uploadedAt
        );

        DownloadData data = new DownloadData(inputStream, file);

        assertSame(inputStream, data.getInputStream());
        assertSame(file, data.getFileRecord());

        assertEquals(10, data.getId());
        assertEquals("hello.txt", data.getFileName());
        assertEquals(5, data.getSize());
        assertEquals(42, data.getOwnerId());
        assertEquals(uploadedAt, data.getUploadedAt());
    }
}