package dowob.xyz.filemanagementwebdav.customerenum;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName Operation
 * @create 2025/7/28
 * @Version 1.0
 **/

public enum Operation {
    READ,   // Read operation
    PUT,  // put operation
    DELETE, // Delete operation
    MOVE,   // Move operation
    COPY,   // Copy operation
    LIST,   // List operation
    MKCOL,   // Make collection (create directory) operation
    LOCK,    // Lock operation
    UNLOCK,  // Unlock operation
    PROPFIND, // Property find operation
    PROPPATCH, // Property patch operation
    UNKNOWN  // Unknown operation, used for error handling
    ;


    public static Operation fromString(String operation) {
        try {
            return Operation.valueOf(operation.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
