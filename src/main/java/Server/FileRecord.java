package Server;

import java.time.LocalDateTime;

public class FileRecord {
    private final long id;
    private final String fileName;
    private final long size;
    private final int ownerId;
    private final LocalDateTime uploadedAt;

    public FileRecord(long id, String fileName, long size, int ownerId, LocalDateTime uploadedAt) {
        this.id = id;
        this.fileName = fileName;
        this.size = size;
        this.ownerId = ownerId;
        this.uploadedAt = uploadedAt;
    }

    public long getId() { return id; }

    public String getFileName() { return fileName; }

    public long getSize() { return size; }

    public int getOwnerId() { return ownerId; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
