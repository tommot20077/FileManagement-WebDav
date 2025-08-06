package dowob.xyz.filemanagementwebdav.data;

import dowob.xyz.filemanagementwebdav.customerenum.Operation;
import io.milton.http.Range;
import lombok.Builder;
import lombok.Data;

import java.io.InputStream;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName ProcessingRequest
 * @create 2025/6/9
 * @Version 1.0
 **/

@Data
@Builder
public class ProcessingRequest {
    private String path;
    private Operation operation; // READ, WRITE, DELETE, MOVE, COPY, LIST, MKCOL
    private String targetPath; // For MOVE/COPY operations
    private String contentType;
    private Long contentLength;
    private InputStream dataStream;
    private Range range;
}