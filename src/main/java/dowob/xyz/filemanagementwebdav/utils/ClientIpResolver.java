package dowob.xyz.filemanagementwebdav.utils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 客戶端 IP 地址解析工具類
 * 
 * 適配 Servlet 環境，支持多種代理環境下的真實 IP 獲取
 * 參考主服務的 ClientIpFilter 實現
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName ClientIpResolver
 * @create 2025/8/5
 * @Version 1.0
 **/
@Slf4j
@Component
public class ClientIpResolver {
    
    // 代理頭部常量
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_FORWARDED = "X-Forwarded";
    private static final String FORWARDED_FOR = "Forwarded-For";
    private static final String FORWARDED = "Forwarded";
    
    // 本地/內網 IP 模式
    private static final String LOCALHOST_IPV4 = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    private static final String UNKNOWN = "unknown";
    
    /**
     * 獲取客戶端真實 IP 地址
     * 
     * @param request HTTP 請求對象
     * @return 客戶端 IP 地址，如果無法獲取則返回 null
     */
    public String getClientIp(HttpServletRequest request) {
        if (request == null) {
            log.warn("HTTP request is null, cannot extract client IP");
            return null;
        }
        
        String clientIp = null;
        
        try {
            // 1. 優先檢查 X-Real-IP（Nginx 反向代理）
            clientIp = request.getHeader(X_REAL_IP);
            if (isValidIp(clientIp)) {
                log.debug("Client IP extracted from X-Real-IP: {}", clientIp);
                return clientIp;
            }
            
            // 2. 檢查 CF-Connecting-IP（Cloudflare CDN）
            clientIp = request.getHeader(CF_CONNECTING_IP);
            if (isValidIp(clientIp)) {
                log.debug("Client IP extracted from CF-Connecting-IP: {}", clientIp);
                return clientIp;
            }
            
            // 3. 檢查 X-Forwarded-For（負載均衡器）
            clientIp = getFirstValidIpFromForwardedFor(request.getHeader(X_FORWARDED_FOR));
            if (isValidIp(clientIp)) {
                log.debug("Client IP extracted from X-Forwarded-For: {}", clientIp);
                return clientIp;
            }
            
            // 4. 檢查其他代理頭部
            clientIp = request.getHeader(X_FORWARDED);
            if (isValidIp(clientIp)) {
                log.debug("Client IP extracted from X-Forwarded: {}", clientIp);
                return clientIp;
            }
            
            clientIp = request.getHeader(FORWARDED_FOR);
            if (isValidIp(clientIp)) {
                log.debug("Client IP extracted from Forwarded-For: {}", clientIp);
                return clientIp;
            }
            
            clientIp = request.getHeader(FORWARDED);
            if (isValidIp(clientIp)) {
                log.debug("Client IP extracted from Forwarded: {}", clientIp);
                return clientIp;
            }
            
            // 5. 最後使用 RemoteAddr
            clientIp = request.getRemoteAddr();
            if (isValidIp(clientIp)) {
                log.debug("Client IP extracted from RemoteAddr: {}", clientIp);
                return clientIp;
            }
            
            log.warn("Unable to extract valid client IP from request");
            return null;
            
        } catch (Exception e) {
            log.error("Error while extracting client IP", e);
            return null;
        }
    }
    
    /**
     * 從 X-Forwarded-For 頭部獲取第一個有效的 IP
     * X-Forwarded-For 格式: client1, proxy1, proxy2
     * 
     * @param forwardedFor X-Forwarded-For 頭部值
     * @return 第一個有效的 IP 地址
     */
    private String getFirstValidIpFromForwardedFor(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.trim().isEmpty()) {
            return null;
        }
        
        // 分割多個 IP 地址
        String[] ips = forwardedFor.split(",");
        
        for (String ip : ips) {
            String trimmedIp = ip.trim();
            if (isValidIp(trimmedIp)) {
                return trimmedIp;
            }
        }
        
        return null;
    }
    
    /**
     * 驗證 IP 地址是否有效
     * 
     * @param ip IP 地址字符串
     * @return true 如果 IP 有效
     */
    private boolean isValidIp(String ip) {
        if (ip == null || ip.trim().isEmpty() || UNKNOWN.equalsIgnoreCase(ip)) {
            return false;
        }
        
        // 標準化 IP 地址，處理端口號
        String cleanIp = normalizeIpAddress(ip.trim());
        
        try {
            InetAddress inetAddress = InetAddress.getByName(cleanIp);
            
            // 檢查是否為有效的 IP 地址格式
            String hostAddress = inetAddress.getHostAddress();
            
            // 標準化 IPv6 localhost 地址
            if (LOCALHOST_IPV6.equals(hostAddress) || "::1".equals(hostAddress)) {
                log.debug("IPv6 localhost 檢測到: {}", hostAddress);
                return true;
            }
            
            if (LOCALHOST_IPV4.equals(hostAddress)) {
                log.debug("IPv4 localhost 檢測到: {}", hostAddress);
                return true;
            }
            
            return true;
            
        } catch (UnknownHostException e) {
            log.debug("無效的 IP 地址格式: {}", cleanIp);
            return false;
        }
    }
    
    /**
     * 標準化 IP 地址，移除端口號並處理 IPv6 格式
     * 
     * @param ip 原始 IP 地址字符串
     * @return 清理後的 IP 地址
     */
    private String normalizeIpAddress(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return ip;
        }
        
        ip = ip.trim();
        
        // 處理 IPv6 地址格式
        if (ip.startsWith("[") && ip.contains("]:")) {
            // IPv6 with port: [2001:db8::1]:8080
            int bracketIndex = ip.indexOf("]:");
            if (bracketIndex > 0) {
                return ip.substring(1, bracketIndex); // 移除 [] 和端口號
            }
        } else if (ip.startsWith("[") && ip.endsWith("]")) {
            // IPv6 without port: [2001:db8::1]
            return ip.substring(1, ip.length() - 1); // 移除 []
        } else if (ip.contains(":") && !isIPv4WithPort(ip)) {
            // 純 IPv6 地址，沒有方括號: 2001:db8::1 或 ::1
            return ip;
        } else if (ip.contains(":") && isIPv4WithPort(ip)) {
            // IPv4 with port: 192.168.1.1:8080
            int portIndex = ip.lastIndexOf(':');
            return ip.substring(0, portIndex);
        }
        
        return ip;
    }
    
    /**
     * 判斷是否為帶端口號的 IPv4 地址
     * 
     * @param ip IP 地址字符串
     * @return true 如果是 IPv4:port 格式
     */
    private boolean isIPv4WithPort(String ip) {
        if (!ip.contains(":")) {
            return false;
        }
        
        // 計算冒號數量，IPv4 地址應該只有一個冒號（用於分隔端口）
        long colonCount = ip.chars().filter(ch -> ch == ':').count();
        if (colonCount != 1) {
            return false; // 多於一個冒號可能是 IPv6
        }
        
        // 檢查冒號前的部分是否像 IPv4
        int colonIndex = ip.lastIndexOf(':');
        String possibleIPv4 = ip.substring(0, colonIndex);
        
        // 簡單檢查 IPv4 格式：包含 3 個點
        return possibleIPv4.chars().filter(ch -> ch == '.').count() == 3;
    }
    
    /**
     * 獲取用戶代理字符串
     * 
     * @param request HTTP 請求對象
     * @return User-Agent 字符串，如果不存在則返回 null
     */
    public String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        String userAgent = request.getHeader("User-Agent");
        
        // 過濾空字符串和 unknown
        if (userAgent != null && !userAgent.trim().isEmpty() && !UNKNOWN.equalsIgnoreCase(userAgent)) {
            return userAgent.trim();
        }
        
        return null;
    }
    
    /**
     * 獲取請求的完整信息
     * 
     * @param request HTTP 請求對象
     * @return 包含 IP 和 User-Agent 的信息對象
     */
    public RequestInfo getRequestInfo(HttpServletRequest request) {
        return new RequestInfo(
            getClientIp(request),
            getUserAgent(request),
            request != null ? request.getRequestURI() : null,
            request != null ? request.getMethod() : null
        );
    }
    
    /**
     * 請求信息記錄類
     */
    public record RequestInfo(
        String clientIp,
        String userAgent,
        String requestUri,
        String method
    ) {}
}