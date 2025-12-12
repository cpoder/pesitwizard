package com.vectis.client.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for resolving placeholders in file paths.
 * 
 * Supported placeholders (PeSIT protocol context):
 * - ${partner} or ${partnerId} - Partner ID (our client ID sent to server)
 * - ${virtualFile} - Virtual file name (PI 12 - Filename)
 * - ${server} or ${serverId} - Server ID
 * - ${serverName} - Server name
 * - ${timestamp} - Current timestamp (yyyyMMdd_HHmmss)
 * - ${date} - Current date (yyyyMMdd)
 * - ${time} - Current time (HHmmss)
 * - ${year}, ${month}, ${day}, ${hour}, ${minute}, ${second}
 * - ${uuid} - Random UUID
 * - ${direction} - Transfer direction (SEND/RECEIVE)
 * 
 * Note: PeSIT does not transmit physical filenames, only virtual file IDs.
 */
@Service
@Slf4j
public class PathPlaceholderService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Resolve all placeholders in a path
     */
    public String resolvePath(String path, PlaceholderContext context) {
        if (path == null || !path.contains("${")) {
            return path;
        }

        Map<String, String> values = buildValueMap(context);

        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(path);

        while (matcher.find()) {
            String placeholder = matcher.group(1).toLowerCase();
            String replacement = values.getOrDefault(placeholder, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        String resolved = result.toString();
        log.debug("Resolved path '{}' to '{}'", path, resolved);
        return resolved;
    }

    private Map<String, String> buildValueMap(PlaceholderContext ctx) {
        Map<String, String> values = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        // Partner (our client ID)
        putIfNotNull(values, "partner", ctx.getPartnerId());
        putIfNotNull(values, "partnerid", ctx.getPartnerId());

        // Virtual file (PI 12 - Filename) - this is NOT a physical filename
        putIfNotNull(values, "virtualfile", ctx.getVirtualFile());

        // Server
        putIfNotNull(values, "server", ctx.getServerId());
        putIfNotNull(values, "serverid", ctx.getServerId());
        putIfNotNull(values, "servername", ctx.getServerName());

        // Direction
        putIfNotNull(values, "direction", ctx.getDirection());

        // Timestamps
        values.put("timestamp", now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        values.put("date", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        values.put("time", now.format(DateTimeFormatter.ofPattern("HHmmss")));
        values.put("year", String.valueOf(now.getYear()));
        values.put("month", String.format("%02d", now.getMonthValue()));
        values.put("day", String.format("%02d", now.getDayOfMonth()));
        values.put("hour", String.format("%02d", now.getHour()));
        values.put("minute", String.format("%02d", now.getMinute()));
        values.put("second", String.format("%02d", now.getSecond()));

        // UUID
        values.put("uuid", UUID.randomUUID().toString());

        return values;
    }

    private void putIfNotNull(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    /**
     * Context for placeholder resolution.
     * Contains only data available from PeSIT protocol.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PlaceholderContext {
        /** Partner ID (our client ID sent to server) */
        private String partnerId;
        /** Virtual file name (PI 12 - Filename) - NOT a physical filename */
        private String virtualFile;
        /** Server ID */
        private String serverId;
        /** Server name */
        private String serverName;
        /** Transfer direction */
        private String direction;
    }

    /**
     * Get list of available placeholders with descriptions
     */
    public static Map<String, String> getAvailablePlaceholders() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("${partner}", "Partner ID (our client ID)");
        placeholders.put("${virtualFile}", "Virtual file name (PI 12)");
        placeholders.put("${server}", "Server ID");
        placeholders.put("${serverName}", "Server name");
        placeholders.put("${timestamp}", "Timestamp (yyyyMMdd_HHmmss)");
        placeholders.put("${date}", "Date (yyyyMMdd)");
        placeholders.put("${time}", "Time (HHmmss)");
        placeholders.put("${year}", "Year (4 digits)");
        placeholders.put("${month}", "Month (01-12)");
        placeholders.put("${day}", "Day (01-31)");
        placeholders.put("${hour}", "Hour (00-23)");
        placeholders.put("${minute}", "Minute (00-59)");
        placeholders.put("${second}", "Second (00-59)");
        placeholders.put("${uuid}", "Random UUID");
        placeholders.put("${direction}", "Transfer direction");
        return placeholders;
    }
}
