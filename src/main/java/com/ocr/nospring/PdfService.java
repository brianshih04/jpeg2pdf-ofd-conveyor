package com.ocr.nospring;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

/**
 * PDF 服務 - 無 Spring Boot（使用與 OFD 相同的逐字符定位算法）
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
                
                // 2. 繪製透明文字層（使用與 OFD 相同的算法）
                drawTransparentTextLayer(contentStream, textBlocks, font, width, height);
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
                    
                    // 2. 繪製透明文字層（使用與 OFD 相同的算法）
                    drawTransparentTextLayer(contentStream, textBlocks, font, width, height);
                }
            }
            
            // 保存
            document.save(outputFile);
        }
    }
    
    /**
     * 繪製透明文字層（整段定位，不用逐字/scaleX）
     */
    private void drawTransparentTextLayer(PDPageContentStream contentStream, List<OcrService.TextBlock> textBlocks, PDFont font, float width, float height) throws Exception {
        // 設置透明度
        org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState extGState = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
        float opacity = (float) config.getTextLayerOpacity();
        extGState.setNonStrokingAlphaConstant(opacity);
        extGState.setStrokingAlphaConstant(opacity);
        contentStream.setGraphicsStateParameters(extGState);
        
        // 設置顏色
        contentStream.setRenderingMode(RenderingMode.FILL);
        contentStream.setNonStrokingColor(config.getTextLayerRed(), config.getTextLayerGreen(), config.getTextLayerBlue());
        
        contentStream.beginText();
        
        for (OcrService.TextBlock block : textBlocks) {
            try {
                String text = block.text.trim();
                if (text == null || text.isEmpty()) continue;
                
                double ocrX = block.x;
                double ocrY = block.y;
                double ocrW = block.width;
                double ocrH = block.height;
                
                // fontSize = box 高度
                float fontSizePt = (float) ocrH;
                
                // 計算文字自然寬度
                float textWidth = font.getStringWidth(text) / 1000f * fontSizePt;
                
                // 如果太寬就縮小 fontSize
                if (textWidth > ocrW) {
                    fontSizePt = (float) (fontSizePt * (ocrW / textWidth));
                    textWidth = font.getStringWidth(text) / 1000f * fontSizePt;
                }
                
                // Y: 文字底部往上抬一點（約 0.1 * fontSize）
                float pdfY = (float) (height - ocrY - ocrH + ocrH * 0.1);
                
                // 判斷直列文字
                boolean isVertical = ocrH > ocrW * 1.5;
                
                if (isVertical) {
                    // 直列：逐字從上到下
                    for (int i = 0; i < text.length(); i++) {
                        String ch = String.valueOf(text.charAt(i));
                        try {
                            float chW = font.getStringWidth(ch) / 1000f * fontSizePt;
                            float chX = (float) (ocrX + (ocrW - chW) / 2);
                            float chY = (float) (height - ocrY - fontSizePt * (i + 1));
                            contentStream.setFont(font, fontSizePt);
                            contentStream.setTextMatrix(1, 0, 0, 1, chX, chY);
                            contentStream.showText(ch);
                        } catch (Exception e) {
                            System.err.println("    [WARN] Skip char '" + ch + "': " + e.getMessage());
                        }
                    }
                } else {
                    // 橫列：整段定位，左對齊
                    contentStream.setFont(font, fontSizePt);
                    contentStream.setTextMatrix(1, 0, 0, 1, (float) ocrX, pdfY);
                    contentStream.showText(text);
                }
            } catch (Exception e) {
                System.err.println("    Error drawing text: " + e.getMessage());
            }
        }
        
        contentStream.endText();
    }
    
    /**
     * 載入字體
     */
    private PDFont loadFont(PDDocument document) throws Exception {
        String fontPath = config.getFontPath();
        
        // 1. 嘗試配置的字體（RTL 語言時跳過預設字型，改用 RTL 專用字型）
        String ocrLang = config.getOcrLanguage();
        boolean isRTL = ocrLang != null && (ocrLang.equals("he") || ocrLang.startsWith("ar") || ocrLang.equals("fa") || ocrLang.equals("ur"));
        
        if (fontPath != null && new File(fontPath).exists() && !isRTL) {
            try {
                PDFont font = PDType0Font.load(document, new File(fontPath));
                System.out.println("    Loaded font (config): " + fontPath);
                return font;
            } catch (Exception e) {
                System.err.println("    Warning: Cannot load font from " + fontPath + ": " + e.getMessage());
            }
        }
        
        // 2. 根據語言選擇字型
        // RTL 語言（希伯來文、阿拉伯文等）
        String[] rtlFonts = {
            "C:/Windows/Fonts/tahoma.ttf",
            "C:/Windows/Fonts/segoeui.ttf",
        };
        if (isRTL) {
            for (String path : rtlFonts) {
                File fontFile = new File(path);
                if (fontFile.exists()) {
                    try {
                        PDFont font = PDType0Font.load(document, fontFile);
                        System.out.println("    Loaded font (RTL): " + path);
                        return font;
                    } catch (Exception e) {
                    }
                }
            }
        }
        
        // CJK 語言
        String[] notoFonts = {
            "C:/OCR/NotoSansSC-VF.ttf",        // 簡體（含繁體）
            "C:/Windows/Fonts/NotoSansTC-VF.ttf", // 繁體
            "C:/Windows/Fonts/NotoSansSC-VF.ttf",
        };
        for (String path : notoFonts) {
            File fontFile = new File(path);
            if (fontFile.exists()) {
                try {
                    PDFont font = PDType0Font.load(document, fontFile);
                    System.out.println("    Loaded font (Noto): " + path);
                    return font;
                } catch (Exception e) {
                    // 繼續嘗試下一個
                }
            }
        }
        
        // 3. Windows TTF 字體（跳過 TTC）
        String[] windowsFonts = {
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/arialuni.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "C:/Windows/Fonts/simkai.ttf",
            "C:/Windows/Fonts/dengxian.ttf"
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
        return org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA;
    }
}
