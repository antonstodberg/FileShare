package Server;

import java.io.InputStream;
import java.time.LocalDateTime;

public class DownloadData {
    private final InputStream inputStream;
    private final FileRecord fileRecord;

    public DownloadData(InputStream inputStream, FileRecord fileRecord) {
        this.inputStream = inputStream;
        this.fileRecord = fileRecord;
    }

    public InputStream getInputStream() { return inputStream; }

    public FileRecord getFileRecord() { return fileRecord; }

    public long getId() { return fileRecord.getId(); }

    public String getFileName() { return fileRecord.getFileName(); }

    public long getSize() { return fileRecord.getSize(); }

    public int getOwnerId() { return fileRecord.getOwnerId(); }

    public LocalDateTime getUploadedAt() { return fileRecord.getUploadedAt(); }
}
