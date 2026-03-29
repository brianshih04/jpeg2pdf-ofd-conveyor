package com.ocr.nospring;

/**
 * 配置類 - 無 Spring Boot
 */
public class Config {
    
    // 簡繁轉換：null(不轉換), "s2t"(簡→繁), "t2s"(繁→簡)
    private String textConvert;
    
    // 字型模式：null/"auto"(自動), "custom"(用戶指定)
    private String fontMode;
    
    private String fontPath;
    
    // OCR 語言（從 config 讀取）
    private String ocrLanguage;
    
    // Tesseract tessdata 路徑
    private String tesseractDataPath = "C:\\OCR\\tessdata";
    
    // Tesseract 語言字串（覆蓋自動映射，例如 "jpn+chi_sim+eng"）
    private String tesseractLang;
    
    // 文字層顏色 (RGB)
    private int textLayerRed = 255;
    private int textLayerGreen = 255;
    private int textLayerBlue = 255;
    
    // 文字層透明度 (0.0 - 1.0)
    private double textLayerOpacity = 0.0001;
    
    public Config() {
        // Don't set default fontPath — let PdfService use its fallback chain (GoNotoKurrent)
        this.fontPath = null;
    }
    
    /**
     * 字體路由由 FontManager 處理，此方法不再使用
     * 請使用 FontManager.getFontInputStream(language) 取得字體
     */
    @Deprecated
    private String getDefaultFontPath() {
        // FontManager 會根據語言自動選擇字體
        // 不再依賴 C:/Windows/Fonts 或其他系統字體路徑
        return null;
    }
    
    public String getFontPath() {
        return fontPath;
    }
    
    public String getOcrLanguage() { return ocrLanguage; }
    
    public void setOcrLanguage(String ocrLanguage) { this.ocrLanguage = ocrLanguage; }
    
    public String getTesseractDataPath() { return tesseractDataPath; }
    
    public void setTesseractDataPath(String path) { this.tesseractDataPath = path; }
    
    public String getTesseractLang() { return tesseractLang; }
    
    public void setTesseractLang(String lang) { this.tesseractLang = lang; }
    
    public void setFontPath(String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            this.fontPath = fontPath;
        } else {
            this.fontPath = null;
        }
    }
    
    public String getTextConvert() {
        return textConvert;
    }
    
    public String getFontMode() {
        return fontMode;
    }

    public void setFontMode(String fontMode) {
        this.fontMode = fontMode;
    }

    public void setTextConvert(String textConvert) {
        this.textConvert = textConvert;
    }
    
    public int getTextLayerRed() {
        return textLayerRed;
    }
    
    public void setTextLayerRed(int textLayerRed) {
        this.textLayerRed = textLayerRed;
    }
    
    public int getTextLayerGreen() {
        return textLayerGreen;
    }
    
    public void setTextLayerGreen(int textLayerGreen) {
        this.textLayerGreen = textLayerGreen;
    }
    
    public int getTextLayerBlue() {
        return textLayerBlue;
    }
    
    public void setTextLayerBlue(int textLayerBlue) {
        this.textLayerBlue = textLayerBlue;
    }
    
    public double getTextLayerOpacity() {
        return textLayerOpacity;
    }
    
    public void setTextLayerOpacity(double textLayerOpacity) {
        this.textLayerOpacity = textLayerOpacity;
    }
    
    /**
     * 設定文字層顏色（支持顏色名稱或 RGB）
     * @param colorName 顏色名稱：white, red, black, blue, green, debug
     */
    public void setTextLayerColor(String colorName) {
        if (colorName == null) return;
        
        switch (colorName.toLowerCase()) {
            case "white":
                textLayerRed = 255; textLayerGreen = 255; textLayerBlue = 255;
                break;
            case "red":
                textLayerRed = 255; textLayerGreen = 0; textLayerBlue = 0;
                break;
            case "black":
                textLayerRed = 0; textLayerGreen = 0; textLayerBlue = 0;
                break;
            case "blue":
                textLayerRed = 0; textLayerGreen = 0; textLayerBlue = 255;
                break;
            case "green":
                textLayerRed = 0; textLayerGreen = 255; textLayerBlue = 0;
                break;
            case "debug":
                // 調試模式：紅色 + 不透明
                textLayerRed = 255; textLayerGreen = 0; textLayerBlue = 0;
                textLayerOpacity = 1.0;
                break;
        }
    }
}
