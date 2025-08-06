package dowob.xyz.filemanagementwebdav.component.factory.mapper;

import com.google.protobuf.ByteString;
import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.ProcessingRequest;
import dowob.xyz.filemanagementwebdav.data.ProcessingResponse;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingProto;
import io.milton.http.Range;
import org.springframework.stereotype.Component;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingProto.FileProcessingRequest;
import dowob.xyz.filemanagementwebdav.grpc.FileProcessingProto.FileProcessingResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebdavToGrpcMapper
 * @create 2025/6/9
 * @Version 1.0
 **/
@Component
public class WebDavToGrpcMapper {

    public FileProcessingRequest toGrpcRequest(ProcessingRequest request) {
        FileProcessingRequest.Builder builder = FileProcessingRequest
                .newBuilder()
                .setPath(request.getPath())
                .setOperation(request.getOperation().name());

        if (request.getTargetPath() != null) {
            builder.setTargetPath(request.getTargetPath());
        }

        if (request.getContentType() != null) {
            builder.setContentType(request.getContentType());
        }

        if (request.getContentLength() != null) {
            builder.setContentLength(request.getContentLength());
        }

        if (request.getRange() != null) {
            builder.setRange(mapRange(request.getRange()));
        }

        // For small files, include data directly
        if (request.getDataStream() != null && request.getContentLength() != null && request.getContentLength() < 1024 * 1024) { // 1MB threshold
            try {
                byte[] data = request.getDataStream().readAllBytes();
                builder.setData(ByteString.copyFrom(data));
            } catch (IOException e) {
                // Skip inline data on error
            }
        }

        return builder.build();
    }


    public ProcessingResponse fromGrpcResponse(FileProcessingResponse grpcResponse) {
        ProcessingResponse.ProcessingResponseBuilder builder = ProcessingResponse.builder().success(grpcResponse.getSuccess());

        if (!grpcResponse.getErrorMessage().isEmpty()) {
            builder.errorMessage(grpcResponse.getErrorMessage());
        }

        if (!grpcResponse.getData().isEmpty()) {
            builder.dataStream(new ByteArrayInputStream(grpcResponse.getData().toByteArray()));
        }

        if (grpcResponse.hasFileMetadata()) {
            builder.metadata(fromGrpcMetadata(grpcResponse.getFileMetadata()));
        }
        
        // 處理 LIST 操作返回的子檔案列表
        if (grpcResponse.getChildrenCount() > 0) {
            List<FileMetadata> children = grpcResponse.getChildrenList().stream()
                    .map(this::fromGrpcMetadata)
                    .collect(Collectors.toList());
            builder.children(children);
        }

        return builder.build();
    }


    public FileMetadata fromGrpcMetadata(FileProcessingProto.FileMetadata grpcMetadata) {
        FileMetadata.FileMetadataBuilder builder = FileMetadata
                .builder()
                .id(grpcMetadata.getId())
                .name(grpcMetadata.getName())
                .path(grpcMetadata.getPath())
                .size(grpcMetadata.getSize())
                .contentType(grpcMetadata.getContentType())
                .isDirectory(grpcMetadata.getIsDirectory())
                .createDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(grpcMetadata.getCreateTimestamp()), ZoneId.systemDefault()))
                .modifiedDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(grpcMetadata.getModifiedTimestamp()), ZoneId.systemDefault()));
        
        // 處理可選欄位
        if (grpcMetadata.hasParentId()) {
            builder.parentId(grpcMetadata.getParentId());
        }
        
        return builder.build();
    }


    private FileProcessingProto.FileRange mapRange(Range range) {
        return FileProcessingProto.FileRange.newBuilder().setStart(range.getStart()).setEnd(range.getFinish()).build();
    }
}