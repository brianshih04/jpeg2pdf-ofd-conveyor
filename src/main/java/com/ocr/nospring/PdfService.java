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
     * 繪製透明文字層（使用與 OFD 相同的逐字符定位算法）
     */
    private void drawTransparentTextLayer(PDPageContentStream contentStream, List<OcrService.TextBlock> textBlocks, PDFont font, float width, float height) throws Exception {
        // DEBUG: 印出文字層設定值
        System.out.println("    [DEBUG] TextLayer color: R=" + config.getTextLayerRed() + " G=" + config.getTextLayerGreen() + " B=" + config.getTextLayerBlue() + " opacity=" + config.getTextLayerOpacity());
        
        // 設置透明度（使用 ExtendedGraphicsState）
        org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState extGState = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
        float opacity = (float) config.getTextLayerOpacity();
        extGState.setNonStrokingAlphaConstant(opacity);
        extGState.setStrokingAlphaConstant(opacity);
        contentStream.setGraphicsStateParameters(extGState);
        System.out.println("    [DEBUG] ExtGState opacity set to: " + opacity);
        
        // 設置渲染模式和顏色
        contentStream.setRenderingMode(RenderingMode.FILL);
        contentStream.setNonStrokingColor(config.getTextLayerRed(), config.getTextLayerGreen(), config.getTextLayerBlue());
        System.out.println("    [DEBUG] NonStrokingColor set to: R=" + config.getTextLayerRed() + " G=" + config.getTextLayerGreen() + " B=" + config.getTextLayerBlue());
        
        for (OcrService.TextBlock block : textBlocks) {
            try {
                // 1. 去除 OCR 文字頭尾的隱形空白
                String text = block.text.trim();
                if (text == null || text.isEmpty()) continue;
                
                // 2. OCR 邊界框
                double ocrX = block.x;
                double ocrY = block.y;
                double ocrW = block.width;
                double ocrH = block.height;
                
                // 3. 字號保持 0.75 完美比例（與 OFD 相同）
                double fontSize = ocrH * 0.75;
                float fontSizePt = (float) fontSize;
                
                // 4. 使用 AWT 字體計算每個字符的實際寬度（與 OFD 相同）
                java.awt.Font awtFont = new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.PLAIN, 1)
                    .deriveFont(fontSizePt);
                java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
                
                // 5. Y 軸使用精確公式（與 OFD 相同，往上移動 0.1 字高）
                double ascentPt = awtFont.getLineMetrics(text, frc).getAscent();
                double paragraphY = (ocrY + (ocrH * 0.72)) - ascentPt - (ocrH * 0.1);
                
                // PDF 的 Y 軸是從下往上，需要轉換
                float pdfY = (float) (height - paragraphY - fontSize);
                
                // 6. 終極算法：逐字符絕對定位（與 OFD 相同）
                double[] charWidths = new double[text.length()];
                double totalAwtWidth = 0;
                
                for (int charIdx = 0; charIdx < text.length(); charIdx++) {
                    String singleChar = String.valueOf(text.charAt(charIdx));
                    double wPt = awtFont.getStringBounds(singleChar, frc).getWidth();
                    
                    // 處理空白字符
                    if (singleChar.equals(" ") && wPt == 0) {
                        wPt = fontSizePt * 0.3;
                    }
                    
                    charWidths[charIdx] = wPt;
                    totalAwtWidth += wPt;
                }
                
                // 7. 計算縮放比例（與 OFD 相同）
                double scaleX = 1.0;
                if (totalAwtWidth > 0) {
                    scaleX = ocrW / totalAwtWidth;
                }
                
                // 8. 逐字符繪製（與 OFD 相同）
                double currentX = ocrX;
                
                // 8. 判斷是否為直列文字（高遠大於寬）
                boolean isVertical = ocrH > ocrW * 1.5;
                
                contentStream.beginText();
                for (int charIdx = 0; charIdx < text.length(); charIdx++) {
                    String singleChar = String.valueOf(text.charAt(charIdx));
                    try {
                        contentStream.setFont(font, fontSizePt);
                        if (isVertical) {
                            // 直列文字：每個字從上到下排列，使用垂直書寫矩陣
                            float charX = (float) (ocrX + (ocrW - charWidths[charIdx] * scaleX) / 2);
                            float charY = (float) (ocrY + (ocrH - fontSize) - (charIdx * fontSizePt));
                            // PDF Y 軸反轉
                            float pdfCharY = (float) (height - charY - fontSize);
                            contentStream.setTextMatrix(1, 0, 0, 1, charX, pdfCharY);
                        } else {
                            // 橫列文字：使用 setTextMatrix 做絕對定位
                            contentStream.setTextMatrix(1, 0, 0, 1, (float) currentX, pdfY);
                        }
                        contentStream.showText(singleChar);
                    } catch (Exception e) {
                        // 跳過無法繪製的字符
                        System.err.println("    [WARN] Skip char '" + singleChar + "' (U+" + String.format("%04X", (int)singleChar.charAt(0)) + "): " + e.getMessage());
                    }
                    
                    // 坐標推進（僅橫列）
                    if (!isVertical) {
                        currentX += (charWidths[charIdx] * scaleX);
                    }
                }
                contentStream.endText();
                
            } catch (Exception e) {
                System.err.println("    Error drawing text: " + e.getMessage());
            }
        }
    }
    
    /**
     * 載入字體
     */
    private PDFont loadFont(PDDocument document) throws Exception {
        String fontPath = config.getFontPath();
        
        // 1. 嘗試配置的字體
        if (fontPath != null && new File(fontPath).exists()) {
            try {
                PDFont font = PDType0Font.load(document, new File(fontPath));
                System.out.println("    Loaded font (config): " + fontPath);
                return font;
            } catch (Exception e) {
                System.err.println("    Warning: Cannot load font from " + fontPath + ": " + e.getMessage());
            }
        }
        
        // 2. 嘗試 NotoSans（最完整的 CJK 覆蓋，TTF 格式）
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
