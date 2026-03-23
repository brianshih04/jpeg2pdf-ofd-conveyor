package com.ocr.nospring;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

/**
 * PDF 服務 - 無 Spring Boot
 */
public class PdfService {
    
    private final Config config;
    
    public PdfService(Config config) {
        this.config = config;
    }
    
    public void generatePdf(BufferedImage image, List<OcrService.TextBlock> textBlocks, File outputFile) throws Exception {
        
        try (PDDocument document = new PDDocument()) {
            // 載入字體
            PDFont font = loadFont(document);
            
            // 建立頁面
            float width = image.getWidth();
            float height = image.getHeight();
            PDPage page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);
            
            // 轉換圖片
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(
                document, imageBytes, "image"
            );
            
            // 繪製內容
            try (PDPageContentStream contentStream = new PDPageContentStream(
                document, page, 
                PDPageContentStream.AppendMode.APPEND, 
                true, 
                true
            )) {
                // 1. 繪製圖片
                contentStream.drawImage(pdImage, 0, 0, width, height);
                
                // 2. 繪製透明文字層
                drawTransparentText(contentStream, textBlocks, font, width, height);
            }
            
            // 保存
            document.save(outputFile);
        }
    }
    
    /**
     * 生成多頁 PDF
     */
    public void generateMultiPagePdf(List<BufferedImage> images, List<List<OcrService.TextBlock>> allTextBlocks, File outputFile) throws Exception {
        
        if (images.size() != allTextBlocks.size()) {
            throw new IllegalArgumentException("Images and text blocks count mismatch");
        }
        
        try (PDDocument document = new PDDocument()) {
            // 載入字體
            PDFont font = loadFont(document);
            
            // 處理每一頁
            for (int pageIndex = 0; pageIndex < images.size(); pageIndex++) {
                BufferedImage image = images.get(pageIndex);
                List<OcrService.TextBlock> textBlocks = allTextBlocks.get(pageIndex);
                
                // 建立頁面
                float width = image.getWidth();
                float height = image.getHeight();
                PDPage page = new PDPage(new PDRectangle(width, height));
                document.addPage(page);
                
                // 轉換圖片
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                byte[] imageBytes = baos.toByteArray();
                
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(
                    document, imageBytes, "image"
                );
                
                // 繪製內容
                try (PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, 
                    PDPageContentStream.AppendMode.APPEND, 
                    true, 
                    true
                )) {
                    // 1. 繪製圖片
                    contentStream.drawImage(pdImage, 0, 0, width, height);
                    
                    // 2. 繪製透明文字層
                    drawTransparentText(contentStream, textBlocks, font, width, height);
                }
            }
            
            // 保存
            document.save(outputFile);
        }
    }
    
    /**
     * 繪製透明文字層
     */
    private void drawTransparentText(PDPageContentStream contentStream, List<OcrService.TextBlock> textBlocks, PDFont font, float width, float height) throws Exception {
        contentStream.setRenderingMode(RenderingMode.NEITHER);
        contentStream.setNonStrokingColor(255, 255, 255); // 白色
        
        for (OcrService.TextBlock block : textBlocks) {
            try {
                // Y 軸轉換（PDF 使用 Y-up）
                float pdfY = (float) (height - block.y - block.height);
                float fontSize = block.fontSize;
                
                // 開始文字繪製
                contentStream.beginText();
                
                try {
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset((float) block.x, pdfY);
                    
                    // 檢查字體是否支持這些字符
                    String text = filterSupportedChars(block.text, font);
                    if (text != null && !text.isEmpty()) {
                        contentStream.showText(text);
                    }
                    
                } finally {
                    // 確保總是調用 endText()
                    contentStream.endText();
                }
                
            } catch (Exception e) {
                System.err.println("    Error drawing text: " + e.getMessage());
            }
        }
    }
    
    /**
     * 過濾字體支持的字符
     */
    private String filterSupportedChars(String text, PDFont font) {
        if (text == null) return null;
        
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                font.getStringWidth(String.valueOf(c));
                sb.append(c);
            } catch (Exception e) {
                // 跳過不支持的字符
            }
        }
        return sb.toString();
    }
    
    /**
     * 載入字體（改進版）
     */
    private PDFont loadFont(PDDocument document) throws Exception {
        String fontPath = config.getFontPath();
        
        // 嘗試配置的字體
        if (fontPath != null && new File(fontPath).exists()) {
            try {
                return PDType0Font.load(document, new File(fontPath));
            } catch (Exception e) {
                System.err.println("    Warning: Cannot load font from " + fontPath + ": " + e.getMessage());
            }
        }
        
        // 嘗試 Windows 常用字體（按優先級）
        String[] windowsFonts = {
            "C:/Windows/Fonts/arial.ttf",           // Arial
            "C:/Windows/Fonts/arialuni.ttf",        // Arial Unicode MS
            "C:/Windows/Fonts/simhei.ttf",          // 黑體
            "C:/Windows/Fonts/simsun.ttc",          // 宋體
            "C:/Windows/Fonts/msyh.ttc",            // 微軟雅黑
            "C:/Windows/Fonts/simkai.ttf",          // 楷體
            "C:/Windows/Fonts/dengxian.ttf"         // 等線
        };
        
        for (String path : windowsFonts) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                try {
                    PDFont font = PDType0Font.load(document, fontFile);
                    System.out.println("    Loaded font: " + path);
                    return font;
                } catch (Exception e) {
                    // 繼續嘗試下一個
                }
            }
        }
        
        // macOS 字體
        String[] macFonts = {
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/STHeiti Light.ttc",
            "/System/Library/Fonts/Hiragino Sans GB.ttc"
        };
        
        for (String path : macFonts) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                try {
                    PDFont font = PDType0Font.load(document, fontFile);
                    System.out.println("    Loaded font: " + path);
                    return font;
                } catch (Exception e) {
                    // 繼續嘗試下一個
                }
            }
        }
        
        // Linux 字體
        String[] linuxFonts = {
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"
        };
        
        for (String path : linuxFonts) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                try {
                    PDFont font = PDType0Font.load(document, fontFile);
                    System.out.println("    Loaded font: " + path);
                    return font;
                } catch (Exception e) {
                    // 繼續嘗試下一個
                }
            }
        }
        
        // 最後使用默認字體（僅支持英文）
        System.err.println("    Warning: Using default Helvetica font (English only)");
        return PDType1Font.HELVETICA;
    }
}
