package com.ocr.nospring;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 雙軌字體路由引擎 - 根據輸出格式 (PDF/OFD) 和語言選擇字體
 *
 * - OFD 輸出：統一使用 GoNotoKurrent-Regular.ttf (避開 ofdrw NPE，Metadata 完整)
 * - PDF 輸出：根據語言精細路由，使用專用字體文件
 */
public class FontManager {

    /**
     * OFD 統一字體 (避開 ofdrw NPE，Metadata 完整)
     */
    private static final String OFD_FONT = "GoNotoKurrent-Regular.ttf";

    /**
     * PDF 最後 fallback 字體 (當專用字體缺失時)
     */
    private static final String PDF_FINAL_FALLBACK = "wqy-ZenHei.ttf";

    /**
     * 字體緩存 - 避免重複載入 10MB+ 字體
     * Key: 字體檔案名稱
     * Value: 載入的 InputStream (使用 ByteArrayInputStream 實現可重複讀取)
     */
    private static final Map<String, byte[]> fontCache = new ConcurrentHashMap<>();

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
     * 載入並緩存字體資料 (避免重複載入 10MB+ 字體)
     * @param fontFileName 字體檔案名稱
     * @return 字體資料 byte array
     */
    private static byte[] loadFontData(String fontFileName) throws IOException {
        // 檢查緩存
        byte[] cached = fontCache.get(fontFileName);
        if (cached != null) {
            return cached;
        }

        // 載入字體
        String resourcePath = "/fonts/" + fontFileName;
        InputStream is = FontManager.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Font not found in resources: " + resourcePath);
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            byte[] fontData = baos.toByteArray();

            // 緩存字體資料
            fontCache.put(fontFileName, fontData);
            System.out.println("    [FontManager] Loaded and cached font: " + fontFileName + " (" + (fontData.length / 1024) + " KB)");
            return fontData;

        } finally {
            is.close();
        }
    }

    /**
     * 取得 PDF 用字體 InputStream (可重複讀取的 ByteArrayInputStream)
     * @param language 語言代碼 (用於選擇適當字體)
     * @return 字體的 InputStream，如果找不到則返回 null
     */
    public static InputStream getFontForPdf(String language) {
        // PDF 與 OFD 統一使用 GoNotoKurrent（全語系支援，Metadata 完整）
        String primaryFont = OFD_FONT;
        try {
            byte[] fontData = loadFontData(primaryFont);
            System.out.println("    [FontManager] PDF font loaded (primary): " + primaryFont);
            return new ByteArrayInputStream(fontData);
        } catch (IOException e) {
            System.out.println("    [FontManager] Primary font failed: " + primaryFont + " - " + e.getMessage());
        }

        // 第二層：嘗試 PDF_FINAL_FALLBACK (wqy-ZenHei.ttf)
        try {
            byte[] fontData = loadFontData(PDF_FINAL_FALLBACK);
            System.out.println("    [FontManager] PDF font loaded (fallback): " + PDF_FINAL_FALLBACK);
            return new ByteArrayInputStream(fontData);
        } catch (IOException e) {
            System.out.println("    [FontManager] Fallback font failed: " + PDF_FINAL_FALLBACK + " - " + e.getMessage());
        }

        // 三層都失敗
        System.err.println("    [FontManager] ERROR: All PDF fonts failed to load for language: " + language);
        return null;
    }

    /**
     * 取得 OFD 用字體 InputStream (優先使用 GoNotoKurrent-Regular.ttf，失敗時 fallback 到 wqy-ZenHei.ttf)
     * @return 字體的 InputStream，如果找不到則返回 null
     */
    public static InputStream getFontForOfd() {
        // 第一層：嘗試 OFD_FONT (GoNotoKurrent-Regular.ttf)
        try {
            byte[] fontData = loadFontData(OFD_FONT);
            System.out.println("    [FontManager] OFD font loaded: " + OFD_FONT);
            return new ByteArrayInputStream(fontData);
        } catch (IOException e) {
            System.out.println("    [FontManager] OFD primary font failed: " + OFD_FONT + " - " + e.getMessage());
        }

        // 第二層：嘗試 PDF_FINAL_FALLBACK (wqy-ZenHei.ttf)
        try {
            byte[] fontData = loadFontData(PDF_FINAL_FALLBACK);
            System.out.println("    [FontManager] OFD font loaded (fallback): " + PDF_FINAL_FALLBACK);
            return new ByteArrayInputStream(fontData);
        } catch (IOException e) {
            System.out.println("    [FontManager] OFD fallback failed: " + PDF_FINAL_FALLBACK + " - " + e.getMessage());
        }

        System.err.println("    [FontManager] ERROR: All OFD fonts failed to load");
        return null;
    }

    /**
     * 取得 PDF 用字體臨時檔案路徑 (供 PDFBox 使用，如果需要)
     * @param language 語言代碼 (用於選擇適當字體)
     * @return 字體檔案的 Path，如果找不到則返回 null
     */
    public static Path getFontTempFileForPdf(String language) throws IOException {
        InputStream is = getFontForPdf(language);
        if (is == null) {
            return null;
        }

        // 建立臨時檔案
        Path tempFile = Files.createTempFile("font_pdf_", ".ttf");
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

        System.out.println("    [FontManager] Created temp PDF font file: " + tempFile);
        return tempFile;
    }

    /**
     * 取得 OFD 用字體臨時檔案路徑 (供 ofdrw 使用)
     * @return 字體檔案的 Path，如果找不到則返回 null
     */
    public static Path getFontTempFileForOfd() throws IOException {
        InputStream is = getFontForOfd();
        if (is == null) {
            return null;
        }

        // 建立臨時檔案
        Path tempFile = Files.createTempFile("font_ofd_", ".ttf");
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

        System.out.println("    [FontManager] Created temp OFD font file: " + tempFile);
        return tempFile;
    }

    /**
     * 清除字體緩存 (測試用或需要重新載入時)
     */
    public static void clearFontCache() {
        fontCache.clear();
        System.out.println("    [FontManager] Font cache cleared");
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
     * 設置當前的 Config 實例 (在程式啟動時呼叫一次)
     * @param config Config 實例
     */
    public static void setConfig(Config config) {
        ConfigHolder.getInstance().setConfig(config);
    }

    // ========== 以下方法已棄用，僅保持相容性 ==========

    /**
     * @deprecated 使用 getFontForPdf(language) 取代
     */
    @Deprecated
    public static InputStream getFontInputStream(String language) {
        return getFontForPdf(language);
    }

    /**
     * @deprecated 使用 getFontTempFileForPdf(language) 或 getFontTempFileForOfd() 取代
     */
    @Deprecated
    public static Path getFontTempFile(String language) throws IOException {
        return getFontTempFileForPdf(language);
    }
}
