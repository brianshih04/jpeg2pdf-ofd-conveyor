package com.ocr.nospring;

import org.ofdrw.layout.OFDDoc;
import org.ofdrw.layout.PageLayout;
import org.ofdrw.layout.VirtualPage;
import org.ofdrw.layout.element.Img;
import org.ofdrw.layout.element.Paragraph;
import org.ofdrw.layout.element.Span;
import org.ofdrw.layout.element.Position;
import org.ofdrw.font.Font;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TTFSubsetter;
import org.apache.fontbox.ttf.TrueTypeFont;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OFD 服務 - 支援字型嵌入（子集），確保跨機器可讀
 */
public class OfdService {
    
    private final Config config;
    
    public OfdService(Config config) {
        this.config = config;
    }
    
    /**
     * 尋找字型檔案路徑（支援 fontMode: auto 自動選擇）
     */
    private String findFontFilePath() {
        // 如果用戶指定了字型路徑，直接用
        if (config.getFontPath() != null && new File(config.getFontPath()).exists()) {
            return config.getFontPath();
        }
        
        // fontMode == "auto" 或 null，根據語言自動選擇
        String lang = config.getOcrLanguage();
        String fontName = resolveAutoFont(lang);
        String fontPath = findFontInDirs(fontName);
        if (fontPath != null) {
            return fontPath;
        }
        
        // 最終 fallback：GoNotoKurrent（通用字型，覆蓋所有語言）
        return findFontInDirs("GoNotoKurrent-Regular.ttf");
    }
    
    /**
     * 根據 OCR 語言自動選擇最適合的字型
     */
    private String resolveAutoFont(String lang) {
        if (lang == null) return "GoNotoKurrent-Regular.ttf";
        
        String l = lang.toLowerCase();
        // 簡中 → GoNotoKurrent（涵蓋 CJK）
        if (l.contains("chs") || l.contains("chinese_chs") || l.equals("sc")) {
            return "GoNotoKurrent-Regular.ttf";
        }
        // 繁中 → GoNotoKurrent（涵蓋 CJK）
        if (l.contains("cht") || l.contains("chinese_cht") || l.equals("tc")) {
            return "GoNotoKurrent-Regular.ttf";
        }
        // 日文 → GoNotoKurrent
        if (l.contains("jpn") || l.contains("japanese")) {
            return "GoNotoKurrent-Regular.ttf";
        }
        // 韓文 → GoNotoKurrent
        if (l.contains("kor") || l.contains("korean")) {
            return "GoNotoKurrent-Regular.ttf";
        }
        // Devanagari (Hindi/Gujarati)
        if (l.contains("hin") || l.contains("guj") || l.contains("devanagari")) {
            return "NotoSansDevanagari-Regular.ttf";
        }
        // 其他 → GoNotoKurrent 通用
        return "GoNotoKurrent-Regular.ttf";
    }
    
    /**
     * 在多個目錄中搜尋字型檔案
     */
    private String findFontInDirs(String fontName) {
        String[] searchDirs = {
            "fonts/",
            "D:/Projects/jpeg2pdf-ofd-conveyor-he/fonts/",
            "D:/Projects/jpeg2pdf-ofd-conveyor-ui-test/fonts/",
            "D:/Projects/jpeg2pdf-ofd-conveyor/fonts/",
            "C:/OCR/"
        };
        for (String dir : searchDirs) {
            String path = dir + fontName;
            if (new File(path).exists()) return path;
        }
        return null;
    }
    
    /**
     * 載入 AWT Font（用於寬度計算）
     */
    private java.awt.Font loadAwtFont(float fontSizePt) throws Exception {
        String fontPath = findFontFilePath();
        if (fontPath != null) {
            return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new File(fontPath))
                .deriveFont(fontSizePt);
        }
        return new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.PLAIN, 1).deriveFont(fontSizePt);
    }
    
    /**
     * 對字型檔進行子集化，只保留指定字元
     * @param fontFilePath 原始字型路徑
     * @param chars 需要保留的字元集合
     * @return 子集化後的臨時 TTF 檔案路徑
     */
    private Path subsetFont(String fontFilePath, Set<Integer> chars) throws Exception {
        TTFParser parser = new TTFParser();
        TrueTypeFont ttf = parser.parse(new File(fontFilePath));
        
        TTFSubsetter subsetter = new TTFSubsetter(ttf);
        // 確保 .notdef 和 space glyph 包含
        subsetter.add(' ');
        
        int skipped = 0;
        for (int codePoint : chars) {
            if (codePoint <= 0x20) continue; // 控制字元已跳過
            // 檢查 glyph 是否存在
            try {
                subsetter.add(codePoint);
            } catch (Exception e) {
                skipped++;
            }
        }
        
        Path subsetPath = Files.createTempFile("font_subset_", ".ttf");
        try (OutputStream os = new FileOutputStream(subsetPath.toFile())) {
            subsetter.writeToStream(os);
        }
        ttf.close();
        
        long originalSize = new File(fontFilePath).length();
        long subsetSize = subsetPath.toFile().length();
        System.out.println("  [OFD] Font subset: " + originalSize / 1024 + " KB -> " + subsetSize / 1024 + " KB (" + (chars.size() - skipped) + " glyphs" + (skipped > 0 ? ", " + skipped + " skipped" : "") + ")");
        
        return subsetPath;
    }
    
    /**
     * 收集所有文字區塊中用到的字元
     */
    private Set<Integer> collectUsedChars(List<List<OcrService.TextBlock>> allTextBlocks) {
        Set<Integer> chars = new HashSet<>();
        for (List<OcrService.TextBlock> pageBlocks : allTextBlocks) {
            for (OcrService.TextBlock block : pageBlocks) {
                if (block.text != null) {
                    for (int i = 0; i < block.text.length(); i++) {
                        chars.add((int) block.text.charAt(i));
                    }
                }
            }
        }
        return chars;
    }
    
    private Set<Integer> collectUsedCharsSingle(List<OcrService.TextBlock> textBlocks) {
        return collectUsedChars(List.of(textBlocks));
    }
    
    /**
     * 註冊字型到 OFD 文檔（嵌入子集）
     * @return ofdrw Font 物件，null 表示找不到字型檔
     */
    private Font registerFont(OFDDoc ofdDoc) throws Exception {
        String fontFilePath = findFontFilePath();
        if (fontFilePath == null) {
            System.out.println("  [OFD] WARNING: No font file found, using system default (not embedded)");
            return null;
        }
        String fontFileName = new File(fontFilePath).getName();
        String fontName = fontFileName.replace(".ttf", "").replace(".otf", "");
        Font ofdFont = new Font(fontName, fontName, Path.of(fontFilePath));
        ofdFont.setEmbeddable(true);
        ofdDoc.getResManager().addFont(ofdFont);
        System.out.println("  [OFD] Font embedded (full): " + fontFilePath);
        return ofdFont;
    }
    
    /**
     * 註冊字型到 OFD 文檔（嵌入子集化字型）
     */
    private Font registerSubsetFont(OFDDoc ofdDoc, Set<Integer> usedChars) throws Exception {
        String fontFilePath = findFontFilePath();
        if (fontFilePath == null) {
            System.out.println("  [OFD] WARNING: No font file found, using system default (not embedded)");
            return null;
        }
        
        Path subsetPath = null;
        try {
            subsetPath = subsetFont(fontFilePath, usedChars);
            // 從檔案路徑提取字型名稱
            String fontFileName = new File(fontFilePath).getName();
            String fontName = fontFileName.replace(".ttf", "").replace(".otf", "");
            Font ofdFont = new Font(fontName, fontName, subsetPath);
            ofdFont.setEmbeddable(true);
            ofdDoc.getResManager().addFont(ofdFont);
            return ofdFont;
        } finally {
            // 清理臨時子集檔案（ofdrw 已讀取完畢）
            if (subsetPath != null) {
                try { Files.deleteIfExists(subsetPath); } catch (Exception ignored) {}
            }
        }
    }
    
    /**
     * 生成多頁 OFD（含字型嵌入）
     */
    public void generateMultiPageOfd(List<BufferedImage> images, List<List<OcrService.TextBlock>> allTextBlocks, File outputFile) throws Exception {
        if (images.size() != allTextBlocks.size()) {
            throw new IllegalArgumentException("Images and text blocks count mismatch");
        }
        
        Path tempDir = Files.createTempDirectory("ofd_multipage_");
        List<Path> tempImages = new ArrayList<>();
        
        try {
            try (OFDDoc ofdDoc = new OFDDoc(outputFile.toPath())) {
                Font ofdFont = registerSubsetFont(ofdDoc, collectUsedChars(allTextBlocks));
                
                for (int pageIndex = 0; pageIndex < images.size(); pageIndex++) {
                    BufferedImage image = images.get(pageIndex);
                    List<OcrService.TextBlock> textBlocks = allTextBlocks.get(pageIndex);
                    
                    Path tempImage = tempDir.resolve("page_" + pageIndex + ".png");
                    ImageIO.write(image, "PNG", tempImage.toFile());
                    tempImages.add(tempImage);
                    
                    double widthMm = image.getWidth() * 25.4 / 72.0;
                    double heightMm = image.getHeight() * 25.4 / 72.0;
                    
                    PageLayout pageLayout = new PageLayout(widthMm, heightMm);
                    pageLayout.setMargin(0d);
                    VirtualPage vPage = new VirtualPage(pageLayout);
                    
                    Img img = new Img(tempImage);
                    img.setPosition(Position.Absolute).setX(0d).setY(0d).setWidth(widthMm).setHeight(heightMm);
                    vPage.add(img);
                    
                    addTextLayer(vPage, textBlocks, ofdFont, pageIndex);
                    ofdDoc.addVPage(vPage);
                }
            }
        } finally {
            for (Path tempImage : tempImages) {
                Files.deleteIfExists(tempImage);
            }
            Files.deleteIfExists(tempDir);
        }
    }
    
    /**
     * 生成單頁 OFD（含字型嵌入）
     */
    public void generateOfd(BufferedImage image, List<OcrService.TextBlock> textBlocks, File outputFile) throws Exception {
        Path tempDir = Files.createTempDirectory("ofd_");
        Path tempImage = tempDir.resolve("page.png");
        ImageIO.write(image, "PNG", tempImage.toFile());
        
        try (OFDDoc ofdDoc = new OFDDoc(outputFile.toPath())) {
            Font ofdFont = registerSubsetFont(ofdDoc, collectUsedCharsSingle(textBlocks));
            
            double widthMm = image.getWidth() * 25.4 / 72.0;
            double heightMm = image.getHeight() * 25.4 / 72.0;
            
            PageLayout pageLayout = new PageLayout(widthMm, heightMm);
            pageLayout.setMargin(0d);
            VirtualPage vPage = new VirtualPage(pageLayout);
            
            Img img = new Img(tempImage);
            img.setPosition(Position.Absolute).setX(0d).setY(0d).setWidth(widthMm).setHeight(heightMm);
            vPage.add(img);
            
            addTextLayer(vPage, textBlocks, ofdFont, 0);
            ofdDoc.addVPage(vPage);
        }
        
        Files.deleteIfExists(tempImage);
        Files.deleteIfExists(tempDir);
    }
    
    /**
     * 添加文字層（共用的核心邏輯）
     */
    private void addTextLayer(VirtualPage vPage, List<OcrService.TextBlock> textBlocks, Font ofdFont, int pageIndex) {
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
        
        // 取得 AWT Font 物件（優先用嵌入字型，確保寬度計算一致）
        java.awt.Font awtBaseFont = (ofdFont != null) ? ofdFont.getFontObj() : null;
        
        for (OcrService.TextBlock block : textBlocks) {
            try {
                String text = block.text.trim();
                if (text == null || text.isEmpty()) continue;
                
                double ocrX = block.x * 25.4 / 72.0;
                double ocrY = block.y * 25.4 / 72.0;
                double ocrW = block.width * 25.4 / 72.0;
                double ocrH = block.height * 25.4 / 72.0;
                
                double fontSizeMm = ocrH * 0.75;
                float fontSizePt = (float) (fontSizeMm * 72.0 / 25.4);
                
                java.awt.Font awtFont = (awtBaseFont != null)
                    ? awtBaseFont.deriveFont(fontSizePt)
                    : loadAwtFont(fontSizePt);
                    
                double ascentPt = awtFont.getLineMetrics(text, frc).getAscent();
                double ascentMm = ascentPt * 25.4 / 72.0;
                double paragraphY = (ocrY + (ocrH * 0.72)) - ascentMm - (ocrH * 0.1);
                
                double[] charWidthsMm = new double[text.length()];
                double totalAwtWidthMm = 0;
                
                for (int i = 0; i < text.length(); i++) {
                    String ch = String.valueOf(text.charAt(i));
                    double wPt = awtFont.getStringBounds(ch, frc).getWidth();
                    if (ch.equals(" ") && wPt == 0) wPt = fontSizePt * 0.3;
                    double wMm = wPt * 25.4 / 72.0;
                    charWidthsMm[i] = wMm;
                    totalAwtWidthMm += wMm;
                }
                
                double scaleX = (totalAwtWidthMm > 0) ? ocrW / totalAwtWidthMm : 1.0;
                double currentX = ocrX;
                
                for (int i = 0; i < text.length(); i++) {
                    String ch = String.valueOf(text.charAt(i));
                    
                    Span span = new Span(ch);
                    span.setFontSize(fontSizeMm);
                    span.setColor(config.getTextLayerRed(), config.getTextLayerGreen(), config.getTextLayerBlue());
                    if (ofdFont != null) {
                        span.setFont(ofdFont);
                    }
                    
                    Paragraph p = new Paragraph();
                    p.add(span);
                    p.setPosition(Position.Absolute);
                    p.setMargin(0d);
                    p.setPadding(0d);
                    p.setLineSpace(0d);
                    p.setWidth(charWidthsMm[i] * scaleX + 10.0);
                    p.setX(currentX);
                    p.setY(paragraphY);
                    p.setOpacity(config.getTextLayerOpacity());
                    
                    vPage.add(p);
                    currentX += (charWidthsMm[i] * scaleX);
                }
                
            } catch (Exception e) {
                System.err.println("    Page " + (pageIndex + 1) + " - Error drawing text: " + e.getMessage());
            }
        }
    }
}
