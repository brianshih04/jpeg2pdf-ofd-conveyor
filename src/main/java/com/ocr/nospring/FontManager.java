package com.ocr.nospring;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 動態字體路由引擎 - 根據語言自動選擇並載入字體
 * 支援從資源目錄載入字體，並提供 PDFBox 和 OFDRW 所需的不同介面
 */
public class FontManager {

    /**
     * 語系與字體檔案名稱的對應規則
     */
    private static final Map<String, String> FONT_MAP = new HashMap<>();

    static {
        // 繁體中文
        FONT_MAP.put("chinese_cht", "NotoSansTC-Regular.ttf");
        FONT_MAP.put("chi_tra", "NotoSansTC-Regular.ttf");
        FONT_MAP.put("tc", "NotoSansTC-Regular.ttf");

        // 簡體中文
        FONT_MAP.put("chinese_chs", "NotoSansSC-Regular.ttf");
        FONT_MAP.put("chi_sim", "NotoSansSC-Regular.ttf");
        FONT_MAP.put("sc", "NotoSansSC-Regular.ttf");

        // 日文
        FONT_MAP.put("japanese", "NotoSansJP-Regular.ttf");
        FONT_MAP.put("jpn", "NotoSansJP-Regular.ttf");

        // 韓文
        FONT_MAP.put("korean", "NotoSansKR-Regular.ttf");
        FONT_MAP.put("kor", "NotoSansKR-Regular.ttf");

        // 阿拉伯/波斯/烏爾都
        FONT_MAP.put("arabic", "NotoSansArabic-Regular.ttf");
        FONT_MAP.put("ara", "NotoSansArabic-Regular.ttf");
        FONT_MAP.put("fas", "NotoSansArabic-Regular.ttf");
        FONT_MAP.put("per", "NotoSansArabic-Regular.ttf");
        FONT_MAP.put("urdu", "NotoSansArabic-Regular.ttf");
        FONT_MAP.put("urd", "NotoSansArabic-Regular.ttf");

        // 希伯來文
        FONT_MAP.put("hebrew", "NotoSansHebrew-Regular.ttf");
        FONT_MAP.put("heb", "NotoSansHebrew-Regular.ttf");

        // 泰文
        FONT_MAP.put("thai", "NotoSansThai-Regular.ttf");
        FONT_MAP.put("tha", "NotoSansThai-Regular.ttf");

        // 印地文/梵文
        FONT_MAP.put("hindi", "NotoSansDevanagari-Regular.ttf");
        FONT_MAP.put("hin", "NotoSansDevanagari-Regular.ttf");
        FONT_MAP.put("san", "NotoSansDevanagari-Regular.ttf");

        // 希臘文
        FONT_MAP.put("greek", "NotoSans-Regular.ttf");
        FONT_MAP.put("gre", "NotoSans-Regular.ttf");
        FONT_MAP.put("ell", "NotoSans-Regular.ttf");

        // 古諾斯語
        FONT_MAP.put("runic", "NotoSansRunic-Regular.ttf");
        FONT_MAP.put("old_norse", "NotoSansRunic-Regular.ttf");
    }

    /**
     * 通用 fallback 字體
     */
    private static final String FALLBACK_FONT = "NotoSans-Regular.ttf";

    /**
     * 根據語言取得字體檔案名稱
     * @param language 語言代碼（例如 "chinese_cht", "jpn", "chinese_cht+eng"）
     * @return 字體檔案名稱
     */
    public static String getFontFileName(String language) {
        if (language == null || language.isEmpty()) {
            return FALLBACK_FONT;
        }

        // 使用 '+' 或 ',' 拆分多語系字串，並去除空白
        Set<String> tokens = Arrays.stream(language.toLowerCase().split("[+,]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        // 精確匹配：使用 Map.get() 而非模糊的 contains()
        for (String token : tokens) {
            String font = FONT_MAP.get(token);
            if (font != null) {
                return font;
            }
        }

        return FALLBACK_FONT;
    }

    /**
     * 檢查字體檔案是否存在於資源目錄
     * @param fontFileName 字體檔案名稱
     * @return true 表示存在
     */
    public static boolean fontResourceExists(String fontFileName) {
        String resourcePath = "/fonts/" + fontFileName;
        return FontManager.class.getResource(resourcePath) != null;
    }

    /**
     * 取得字體的 InputStream（供 PDFBox 使用）
     * @param language 語言代碼
     * @return 字體的 InputStream，如果找不到則返回 null
     */
    public static InputStream getFontInputStream(String language) {
        // 使用者自定義字體（最高優先級）
        String customFontPath = ConfigHolder.getInstance().getFontPath();
        if (customFontPath != null && !customFontPath.isEmpty()) {
            File fontFile = new File(customFontPath);
            if (fontFile.exists()) {
                try {
                    System.out.println("    [FontManager] Using custom font: " + customFontPath);
                    return new FileInputStream(fontFile);
                } catch (FileNotFoundException e) {
                    System.err.println("    [FontManager] Cannot open custom font: " + e.getMessage());
                }
            }
        }

        // 根據語言選擇字體
        String fontFileName = getFontFileName(language);
        String resourcePath = "/fonts/" + fontFileName;
        InputStream is = FontManager.class.getResourceAsStream(resourcePath);

        if (is != null) {
            System.out.println("    [FontManager] Loaded font from resources: " + resourcePath);
            return is;
        }

        // 嘗試 fallback 字體
        if (!fontFileName.equals(FALLBACK_FONT)) {
            String fallbackPath = "/fonts/" + FALLBACK_FONT;
            is = FontManager.class.getResourceAsStream(fallbackPath);
            if (is != null) {
                System.out.println("    [FontManager] Using fallback font: " + fallbackPath);
                return is;
            }
        }

        System.err.println("    [FontManager] No font found in resources for language: " + language);
        return null;
    }

    /**
     * 取得字體的臨時檔案路徑（供 OFDRW 使用）
     * 臨時檔案會在 JVM 關閉時自動刪除
     * @param language 語言代碼
     * @return 字體檔案的 Path，如果找不到則返回 null
     */
    public static Path getFontTempFile(String language) throws IOException {
        InputStream is = getFontInputStream(language);
        if (is == null) {
            return null;
        }

        // 建立臨時檔案
        Path tempFile = Files.createTempFile("font_", ".ttf");
        tempFile.toFile().deleteOnExit();

        // 寫入字體資料
        try (OutputStream os = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } finally {
            is.close();
        }

        System.out.println("    [FontManager] Created temp font file: " + tempFile);
        return tempFile;
    }

    /**
     * 單例模式：保存當前 Config 實例的 holder
     */
    private static class ConfigHolder {
        private static final ConfigHolder INSTANCE = new ConfigHolder();
        private Config config;

        public static ConfigHolder getInstance() {
            return INSTANCE;
        }

        public void setConfig(Config config) {
            this.config = config;
        }

        public String getFontPath() {
            return config != null ? config.getFontPath() : null;
        }
    }

    /**
     * 設置當前的 Config 實例（在程式啟動時呼叫一次）
     * @param config Config 實例
     */
    public static void setConfig(Config config) {
        ConfigHolder.getInstance().setConfig(config);
    }
}
