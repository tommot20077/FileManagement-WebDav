package dowob.xyz.filemanagementwebdav.component.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * IP 白名單服務
 * 
 * 提供 IP 白名單和黑名單管理功能，支援 CIDR 表示法和 IP 範圍。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName IpWhitelistService
 * @create 2025/8/5
 * @Version 1.0
 */
@Slf4j
@Service
public class IpWhitelistService {
    
    private final boolean whitelistEnabled;
    private final Set<String> whitelistedIps;
    private final Set<String> blacklistedIps;
    private final Set<IpRange> whitelistedRanges;
    private final Set<IpRange> blacklistedRanges;
    
    // 快取解析結果以提升性能
    private final ConcurrentHashMap<String, Boolean> whitelistCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> blacklistCache = new ConcurrentHashMap<>();
    
    /**
     * IP 範圍類
     */
    private static class IpRange {
        private final long startIp;
        private final long endIp;
        
        public IpRange(String cidr) throws UnknownHostException {
            if (cidr.contains("/")) {
                // CIDR 表示法 (e.g., 192.168.1.0/24)
                String[] parts = cidr.split("/");
                if (parts.length != 2 || parts[1].trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
                }
                String baseIp = parts[0];
                int prefixLength = Integer.parseInt(parts[1]);
                
                // 驗證前綴長度範圍
                if (prefixLength < 0 || prefixLength > 32) {
                    throw new IllegalArgumentException("Invalid CIDR prefix length: " + prefixLength + " (must be 0-32)");
                }
                
                long baseIpLong = ipToLong(InetAddress.getByName(baseIp).getAddress());
                long mask = 0xFFFFFFFFL << (32 - prefixLength);
                
                this.startIp = baseIpLong & mask;
                this.endIp = startIp | (0xFFFFFFFFL >>> prefixLength);
            } else if (cidr.contains("-")) {
                // IP 範圍 (e.g., 192.168.1.1-192.168.1.100)
                String[] parts = cidr.split("-");
                if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                    throw new IllegalArgumentException("Invalid IP range format: " + cidr);
                }
                this.startIp = ipToLong(InetAddress.getByName(parts[0].trim()).getAddress());
                this.endIp = ipToLong(InetAddress.getByName(parts[1].trim()).getAddress());
            } else {
                // 單個 IP
                long ip = ipToLong(InetAddress.getByName(cidr).getAddress());
                this.startIp = ip;
                this.endIp = ip;
            }
        }
        
        public boolean contains(String ip) {
            try {
                long ipLong = ipToLong(InetAddress.getByName(ip).getAddress());
                return ipLong >= startIp && ipLong <= endIp;
            } catch (UnknownHostException e) {
                log.warn("Invalid IP address: {}", ip);
                return false;
            }
        }
        
        private static long ipToLong(byte[] ip) {
            long result = 0;
            for (byte b : ip) {
                result = (result << 8) | (b & 0xFF);
            }
            return result;
        }
    }
    
    /**
     * 構造函數
     */
    public IpWhitelistService() {
        // 專用 WebDAV 子服務中 IP 白名單功能預設啟用
        this.whitelistEnabled = true;
        this.whitelistedIps = new HashSet<>();
        this.blacklistedIps = new HashSet<>();
        this.whitelistedRanges = new HashSet<>();
        this.blacklistedRanges = new HashSet<>();
        
        // 預設添加本地 IP 到白名單
        addLocalIpsToWhitelist();
        
        log.info("IpWhitelistService initialized - enabled: {}, whitelist size: {}, blacklist size: {}", 
                whitelistEnabled, whitelistedIps.size() + whitelistedRanges.size(), 
                blacklistedIps.size() + blacklistedRanges.size());
    }
    
    /**
     * 測試用構造函數（允許自定義初始 IP 列表）
     */
    public IpWhitelistService(List<String> whitelistIps, List<String> blacklistIps) {
        this.whitelistEnabled = true;
        this.whitelistedIps = new HashSet<>();
        this.blacklistedIps = new HashSet<>();
        this.whitelistedRanges = new HashSet<>();
        this.blacklistedRanges = new HashSet<>();
        
        // 解析白名單
        if (whitelistIps != null) {
            for (String ip : whitelistIps) {
                parseAndAddIp(ip.trim(), true);
            }
        }
        
        // 解析黑名單
        if (blacklistIps != null) {
            for (String ip : blacklistIps) {
                parseAndAddIp(ip.trim(), false);
            }
        }
        
        // 預設添加本地 IP 到白名單
        addLocalIpsToWhitelist();
        
        log.info("IpWhitelistService initialized (test mode) - enabled: {}, whitelist size: {}, blacklist size: {}", 
                whitelistEnabled, whitelistedIps.size() + whitelistedRanges.size(), 
                blacklistedIps.size() + blacklistedRanges.size());
    }
    
    /**
     * 檢查 IP 是否在白名單中
     */
    public boolean isWhitelisted(String ip) {
        if (!whitelistEnabled) {
            return true; // 如果白名單未啟用，允許所有 IP
        }
        
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        // 檢查快取
        Boolean cached = whitelistCache.get(ip);
        if (cached != null) {
            return cached;
        }
        
        boolean result = checkWhitelist(ip);
        whitelistCache.put(ip, result);
        
        return result;
    }
    
    /**
     * 檢查 IP 是否在黑名單中
     */
    public boolean isBlacklisted(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        // 檢查快取
        Boolean cached = blacklistCache.get(ip);
        if (cached != null) {
            return cached;
        }
        
        boolean result = checkBlacklist(ip);
        blacklistCache.put(ip, result);
        
        return result;
    }
    
    /**
     * 是否啟用白名單
     */
    public boolean isEnabled() {
        return whitelistEnabled;
    }
    
    /**
     * 動態添加 IP 到白名單
     */
    public void addToWhitelist(String ip) {
        parseAndAddIp(ip, true);
        whitelistCache.clear(); // 清空快取
        log.info("Added IP to whitelist: {}", ip);
    }
    
    /**
     * 動態添加 IP 到黑名單
     */
    public void addToBlacklist(String ip) {
        parseAndAddIp(ip, false);
        blacklistCache.clear(); // 清空快取
        log.info("Added IP to blacklist: {}", ip);
    }
    
    /**
     * 從白名單移除 IP
     */
    public void removeFromWhitelist(String ip) {
        whitelistedIps.remove(ip);
        // 移除相關的範圍比較複雜，這裡簡化處理
        whitelistCache.clear();
        log.info("Removed IP from whitelist: {}", ip);
    }
    
    /**
     * 從黑名單移除 IP
     */
    public void removeFromBlacklist(String ip) {
        blacklistedIps.remove(ip);
        blacklistCache.clear();
        log.info("Removed IP from blacklist: {}", ip);
    }
    
    /**
     * 實際檢查白名單
     */
    private boolean checkWhitelist(String ip) {
        // 檢查單個 IP
        if (whitelistedIps.contains(ip)) {
            return true;
        }
        
        // 檢查 IP 範圍
        for (IpRange range : whitelistedRanges) {
            if (range.contains(ip)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 實際檢查黑名單
     */
    private boolean checkBlacklist(String ip) {
        // 檢查單個 IP
        if (blacklistedIps.contains(ip)) {
            return true;
        }
        
        // 檢查 IP 範圍
        for (IpRange range : blacklistedRanges) {
            if (range.contains(ip)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 解析並添加 IP
     */
    private void parseAndAddIp(String ip, boolean isWhitelist) {
        try {
            if (ip.contains("/") || ip.contains("-")) {
                // IP 範圍
                IpRange range = new IpRange(ip);
                if (isWhitelist) {
                    whitelistedRanges.add(range);
                } else {
                    blacklistedRanges.add(range);
                }
            } else {
                // 單個 IP
                if (isValidIp(ip)) {
                    if (isWhitelist) {
                        whitelistedIps.add(ip);
                    } else {
                        blacklistedIps.add(ip);
                    }
                } else {
                    log.warn("Invalid IP address: {}", ip);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing IP: {}", ip, e);
        }
    }
    
    /**
     * 添加本地 IP 到白名單
     */
    private void addLocalIpsToWhitelist() {
        // 添加本地環回地址
        whitelistedIps.add("127.0.0.1");
        whitelistedIps.add("::1");
        whitelistedIps.add("localhost");
        
        // 添加私有網路範圍
        try {
            whitelistedRanges.add(new IpRange("192.168.0.0/16"));  // 私有網路 A
            whitelistedRanges.add(new IpRange("172.16.0.0/12"));   // 私有網路 B
            whitelistedRanges.add(new IpRange("10.0.0.0/8"));      // 私有網路 C
        } catch (UnknownHostException e) {
            log.error("Error adding local network ranges", e);
        }
    }
    
    /**
     * 驗證 IP 地址格式
     */
    private boolean isValidIp(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * 獲取白名單統計
     */
    public String getWhitelistStats() {
        return String.format("Whitelist - IPs: %d, Ranges: %d, Cache hits: %d", 
                           whitelistedIps.size(), whitelistedRanges.size(), whitelistCache.size());
    }
    
    /**
     * 獲取黑名單統計
     */
    public String getBlacklistStats() {
        return String.format("Blacklist - IPs: %d, Ranges: %d, Cache hits: %d", 
                           blacklistedIps.size(), blacklistedRanges.size(), blacklistCache.size());
    }
    
    /**
     * 清空快取
     */
    public void clearCache() {
        whitelistCache.clear();
        blacklistCache.clear();
        log.info("IP whitelist/blacklist cache cleared");
    }
}