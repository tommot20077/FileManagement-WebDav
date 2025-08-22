package dowob.xyz.filemanagementwebdav.component.security;

import dowob.xyz.filemanagementwebdav.config.properties.WebDavSecurityProperties;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 白名單服務
 * 
 * 提供 IP 白名單和黑名單管理功能，支援 CIDR 表示法、IP 範圍、IPv4 和 IPv6。
 * 
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName IpWhitelistService
 * @create 2025/8/5
 * @Version 1.0
 */
@Log4j2
@Service
public class IpWhitelistService {
    
    private final WebDavSecurityProperties securityProperties;
    private final boolean whitelistEnabled;
    private final Set<String> whitelistedIps;
    private final Set<String> blacklistedIps;
    private final Set<IpRange> whitelistedRanges;
    private final Set<IpRange> blacklistedRanges;
    
    // 快取解析結果以提升性能
    private final ConcurrentHashMap<String, Boolean> whitelistCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> blacklistCache = new ConcurrentHashMap<>();
    
    /**
     * IP 範圍類 - 支援 IPv4 和 IPv6
     */
    private static class IpRange {
        private final BigInteger startIp;
        private final BigInteger endIp;
        private final boolean isIpv6;
        
        public IpRange(String cidr) throws UnknownHostException {
            if (cidr.contains("/")) {
                // CIDR 表示法
                String[] parts = cidr.split("/");
                if (parts.length != 2 || parts[1].trim().isEmpty()) {
                    throw new IllegalArgumentException("無效的 CIDR 格式: " + cidr);
                }
                String baseIp = parts[0].trim();
                int prefixLength = Integer.parseInt(parts[1].trim());
                
                InetAddress inetAddress = InetAddress.getByName(baseIp);
                this.isIpv6 = inetAddress instanceof Inet6Address;
                
                // 驗證前綴長度範圍
                int maxPrefixLength = isIpv6 ? 128 : 32;
                if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                    throw new IllegalArgumentException("無效的 CIDR 前綴長度: " + prefixLength + 
                        " (必須在 0-" + maxPrefixLength + " 之間，" + (isIpv6 ? "IPv6" : "IPv4") + ")");
                }
                
                BigInteger baseIpBig = ipToBigInteger(inetAddress);
                BigInteger mask = createMask(prefixLength, isIpv6);
                
                this.startIp = baseIpBig.and(mask);
                this.endIp = startIp.or(mask.not().and(createFullMask(isIpv6)));
                
            } else if (cidr.contains("-")) {
                // IP 範圍
                String[] parts = cidr.split("-");
                if (parts.length != 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
                    throw new IllegalArgumentException("無效的 IP 範圍格式: " + cidr);
                }
                
                InetAddress startAddr = InetAddress.getByName(parts[0].trim());
                InetAddress endAddr = InetAddress.getByName(parts[1].trim());
                
                // 確保兩個 IP 是同一種類型（IPv4 或 IPv6）
                boolean startIsIpv6 = startAddr instanceof Inet6Address;
                boolean endIsIpv6 = endAddr instanceof Inet6Address;
                if (startIsIpv6 != endIsIpv6) {
                    throw new IllegalArgumentException("IP 範圍必須使用相同的協定版本: " + cidr);
                }
                
                this.isIpv6 = startIsIpv6;
                this.startIp = ipToBigInteger(startAddr);
                this.endIp = ipToBigInteger(endAddr);
                
            } else {
                // 單個 IP
                InetAddress inetAddress = InetAddress.getByName(cidr.trim());
                this.isIpv6 = inetAddress instanceof Inet6Address;
                BigInteger ip = ipToBigInteger(inetAddress);
                this.startIp = ip;
                this.endIp = ip;
            }
        }
        
        public boolean contains(String ip) {
            try {
                InetAddress inetAddress = InetAddress.getByName(ip);
                boolean ipIsIpv6 = inetAddress instanceof Inet6Address;
                
                // IP 類型必須匹配
                if (ipIsIpv6 != isIpv6) {
                    return false;
                }
                
                BigInteger ipBig = ipToBigInteger(inetAddress);
                return ipBig.compareTo(startIp) >= 0 && ipBig.compareTo(endIp) <= 0;
                
            } catch (UnknownHostException e) {
                log.warn("無效的 IP 地址: {}", ip);
                return false;
            }
        }
        
        private static BigInteger ipToBigInteger(InetAddress inetAddress) {
            byte[] bytes = inetAddress.getAddress();
            // 確保 BigInteger 是正數
            return new BigInteger(1, bytes);
        }
        
        private static BigInteger createMask(int prefixLength, boolean isIpv6) {
            int totalBits = isIpv6 ? 128 : 32;
            if (prefixLength == 0) {
                return BigInteger.ZERO;
            }
            if (prefixLength >= totalBits) {
                return createFullMask(isIpv6);
            }
            
            return BigInteger.valueOf(-1).shiftLeft(totalBits - prefixLength);
        }
        
        private static BigInteger createFullMask(boolean isIpv6) {
            int totalBits = isIpv6 ? 128 : 32;
            return BigInteger.ONE.shiftLeft(totalBits).subtract(BigInteger.ONE);
        }
    }
    
    /**
     * 構造函數 - 從配置文件初始化
     */
    @Autowired
    public IpWhitelistService(WebDavSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.whitelistEnabled = securityProperties.getIp().getWhitelist().isEnabled();
        this.whitelistedIps = new HashSet<>();
        this.blacklistedIps = new HashSet<>();
        this.whitelistedRanges = new HashSet<>();
        this.blacklistedRanges = new HashSet<>();
        
        // 從配置載入白名單
        List<String> configWhitelistIps = securityProperties.getIp().getWhitelist().getIps();
        if (configWhitelistIps != null && !configWhitelistIps.isEmpty()) {
            for (String ip : configWhitelistIps) {
                if (ip != null && !ip.trim().isEmpty()) {
                    parseAndAddIp(ip.trim(), true);
                }
            }
            log.info("從配置載入白名單 IP: {}", configWhitelistIps.size());
        }
        
        // 從配置載入黑名單
        List<String> configBlacklistIps = securityProperties.getIp().getBlacklist().getIps();
        if (configBlacklistIps != null && !configBlacklistIps.isEmpty()) {
            for (String ip : configBlacklistIps) {
                if (ip != null && !ip.trim().isEmpty()) {
                    parseAndAddIp(ip.trim(), false);
                }
            }
            log.info("從配置載入黑名單 IP: {}", configBlacklistIps.size());
        }
        
        // 始終添加本地 IP 到白名單（安全起見）
        addLocalIpsToWhitelist();
        
        log.info("IpWhitelistService 初始化完成 - 啟用: {}, 白名單大小: {}, 黑名單大小: {}", 
                whitelistEnabled, 
                whitelistedIps.size() + whitelistedRanges.size(), 
                blacklistedIps.size() + blacklistedRanges.size());
    }
    
    /**
     * 測試用構造函數（允許自定義初始 IP 列表）
     */
    public IpWhitelistService(List<String> whitelistIps, List<String> blacklistIps) {
        this.securityProperties = null;
        this.whitelistEnabled = true;
        this.whitelistedIps = new HashSet<>();
        this.blacklistedIps = new HashSet<>();
        this.whitelistedRanges = new HashSet<>();
        this.blacklistedRanges = new HashSet<>();
        
        // 解析白名單
        if (whitelistIps != null) {
            for (String ip : whitelistIps) {
                if (ip != null && !ip.trim().isEmpty()) {
                    parseAndAddIp(ip.trim(), true);
                }
            }
        }
        
        // 解析黑名單
        if (blacklistIps != null) {
            for (String ip : blacklistIps) {
                if (ip != null && !ip.trim().isEmpty()) {
                    parseAndAddIp(ip.trim(), false);
                }
            }
        }
        
        // 預設添加本地 IP 到白名單
        addLocalIpsToWhitelist();
        
        log.info("IpWhitelistService 初始化完成 (測試模式) - 啟用: {}, 白名單大小: {}, 黑名單大小: {}", 
                whitelistEnabled, 
                whitelistedIps.size() + whitelistedRanges.size(), 
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
        
        // 標準化 IP 地址
        String normalizedIp = normalizeIp(ip.trim());
        
        // 檢查快取
        Boolean cached = whitelistCache.get(normalizedIp);
        if (cached != null) {
            return cached;
        }
        
        boolean result = checkWhitelist(normalizedIp);
        whitelistCache.put(normalizedIp, result);
        
        return result;
    }
    
    /**
     * 檢查 IP 是否在黑名單中
     */
    public boolean isBlacklisted(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        
        // 標準化 IP 地址
        String normalizedIp = normalizeIp(ip.trim());
        
        // 檢查快取
        Boolean cached = blacklistCache.get(normalizedIp);
        if (cached != null) {
            return cached;
        }
        
        boolean result = checkBlacklist(normalizedIp);
        blacklistCache.put(normalizedIp, result);
        
        return result;
    }
    
    /**
     * 標準化 IP 地址 - 處理 IPv6 格式
     */
    private String normalizeIp(String ip) {
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            String normalized = inetAddress.getHostAddress();
            
            // IPv6 特殊處理：標準化 localhost
            if (normalized.equals("0:0:0:0:0:0:0:1") || normalized.equals("::1")) {
                return "::1";
            }
            
            return normalized;
        } catch (UnknownHostException e) {
            // 如果無法解析，返回原始 IP
            return ip;
        }
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
        log.info("添加 IP 到白名單: {}", ip);
    }
    
    /**
     * 動態添加 IP 到黑名單
     */
    public void addToBlacklist(String ip) {
        parseAndAddIp(ip, false);
        blacklistCache.clear(); // 清空快取
        log.info("添加 IP 到黑名單: {}", ip);
    }
    
    /**
     * 從白名單移除 IP
     */
    public void removeFromWhitelist(String ip) {
        whitelistedIps.remove(ip);
        // 移除相關的範圍比較複雜，這裡簡化處理
        whitelistCache.clear();
        log.info("從白名單移除 IP: {}", ip);
    }
    
    /**
     * 從黑名單移除 IP
     */
    public void removeFromBlacklist(String ip) {
        blacklistedIps.remove(ip);
        blacklistCache.clear();
        log.info("從黑名單移除 IP: {}", ip);
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
                    String normalizedIp = normalizeIp(ip);
                    if (isWhitelist) {
                        whitelistedIps.add(normalizedIp);
                    } else {
                        blacklistedIps.add(normalizedIp);
                    }
                } else {
                    log.warn("無效的 IP 地址: {}", ip);
                }
            }
        } catch (Exception e) {
            log.error("解析 IP 時發生錯誤: {}", ip, e);
        }
    }
    
    /**
     * 添加本地 IP 到白名單
     */
    private void addLocalIpsToWhitelist() {
        // 添加本地環回地址
        whitelistedIps.add("127.0.0.1");  // IPv4 localhost
        whitelistedIps.add("::1");        // IPv6 localhost
        whitelistedIps.add("localhost");
        
        // 添加私有網路範圍
        try {
            // IPv4 私有網路
            whitelistedRanges.add(new IpRange("192.168.0.0/16"));  // 私有網路 A
            whitelistedRanges.add(new IpRange("172.16.0.0/12"));   // 私有網路 B
            whitelistedRanges.add(new IpRange("10.0.0.0/8"));      // 私有網路 C
            
            // IPv6 私有網路和特殊地址
            whitelistedRanges.add(new IpRange("fc00::/7"));       // IPv6 唯一本地地址
            whitelistedRanges.add(new IpRange("fe80::/10"));      // IPv6 連結本地地址
            
        } catch (UnknownHostException e) {
            log.error("添加本地網路範圍時發生錯誤", e);
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
        return String.format("白名單 - IP: %d, 範圍: %d, 快取命中: %d", 
                           whitelistedIps.size(), whitelistedRanges.size(), whitelistCache.size());
    }
    
    /**
     * 獲取黑名單統計
     */
    public String getBlacklistStats() {
        return String.format("黑名單 - IP: %d, 範圍: %d, 快取命中: %d", 
                           blacklistedIps.size(), blacklistedRanges.size(), blacklistCache.size());
    }
    
    /**
     * 清空所有快取
     */
    public void clearCache() {
        whitelistCache.clear();
        blacklistCache.clear();
        log.info("已清空 IP 檢查快取");
    }
}