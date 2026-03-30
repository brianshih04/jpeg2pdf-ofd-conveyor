package com.ocr.nospring;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internationalization Manager - Singleton
 * Loads Java Properties files from resources/i18n/
 */
public final class I18nManager {
    
    private static I18nManager instance;
    private final Map<String, Properties> languages = new ConcurrentHashMap<>();
    private String currentLanguage = "zh-TW";  // Default to Traditional Chinese
    private final ObjectMapper mapper = new ObjectMapper();
    
    // Language code to properties file mapping
    private static final Map<String, String> LANG_FILE_MAP = new LinkedHashMap<>();
    static {
        LANG_FILE_MAP.put("zh-TW", "messages_zh_TW.properties");
        LANG_FILE_MAP.put("zh-CN", "messages_zh_CN.properties");
        LANG_FILE_MAP.put("en", "messages_en.properties");
    }
    
    private I18nManager() {
        loadAllLanguages();
    }
    
    public static synchronized I18nManager getInstance() {
        if (instance == null) {
            instance = new I18nManager();
        }
        return instance;
    }
    
    private void loadAllLanguages() {
        for (Map.Entry<String, String> entry : LANG_FILE_MAP.entrySet()) {
            String langCode = entry.getKey();
            String fileName = entry.getValue();
            try {
                InputStream is = getClass().getResourceAsStream("/i18n/" + fileName);
                if (is != null) {
                    Properties props = new Properties();
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        props.load(reader);
                    }
                    languages.put(langCode, props);
                    System.out.println("[I18nManager] Loaded: " + langCode + " (" + props.size() + " keys)");
                } else {
                    System.err.println("[I18nManager] File not found: /i18n/" + fileName);
                }
            } catch (Exception e) {
                System.err.println("[I18nManager] Failed to load: " + langCode + " - " + e.getMessage());
            }
        }
    }
    
    public void setLanguage(String langCode) {
        if (languages.containsKey(langCode)) {
            this.currentLanguage = langCode;
        } else {
            this.currentLanguage = "zh-TW";  // Default fallback
        }
    }
    
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Get message by key (alias for getMessage)
     */
    public String get(String key) {
        return getMessage(key);
    }
    
    /**
     * Get message for key in current language.
     * Falls back to English if key not found in current language.
     */
    public String getMessage(String key) {
        return getMessage(key, currentLanguage);
    }
    
    /**
     * Get message for key in specified language.
     * Falls back to English if not found.
     */
    public String getMessage(String key, String langCode) {
        // Try requested language
        Properties props = languages.get(langCode);
        if (props != null && props.containsKey(key)) {
            return props.getProperty(key);
        }
        
        // Fallback to English
        props = languages.get("en");
        if (props != null && props.containsKey(key)) {
            return props.getProperty(key);
        }
        
        // Fallback to Traditional Chinese
        props = languages.get("zh-TW");
        if (props != null && props.containsKey(key)) {
            return props.getProperty(key);
        }
        
        // Last resort: return key itself
        return key;
    }
    
    /**
     * Get all messages for current language as JSON string.
     * Used for JavaScript bridge to get all translations at once.
     */
    public String getAllMessagesAsJson() {
        return getAllMessagesAsJson(currentLanguage);
    }
    
    public String getAllMessagesAsJson(String langCode) {
        Properties props = languages.get(langCode);
        if (props == null) {
            props = languages.get("en");
        }
        if (props == null) {
            return "{}";
        }
        
        try {
            Map<String, String> map = new LinkedHashMap<>();
            for (String key : props.stringPropertyNames()) {
                map.put(key, props.getProperty(key));
            }
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
