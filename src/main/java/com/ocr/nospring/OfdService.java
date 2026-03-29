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
     * 尋找 OFD 字型檔案路徑（統一使用 GoNotoKurrent-Regular.ttf）
     * OFD 使用統一字體以避開 ofdrw NPE 問題，且 GoNotoKurrent Metadata 完整
     */
    private String findFontFilePath() throws Exception {
        // OFD 統一使用 GoNotoKurrent-Regular.ttf (避開 ofdrw NPE)
        Path fontPath = FontManager.getFontTempFileForOfd();

        if (fontPath != null) {
            return fontPath.toString();
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
     * @return 子集化後的臨時 TTF 檔案路徑，如果失敗返回原始路徑
     */
    private Path subsetFont(String fontFilePath, Set<Integer> chars) throws Exception {
        try {
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
                    String chStr = new String(Character.toChars(codePoint));
                    System.err.println("    [OFD] Skip missing glyph: '" + chStr + "' (U+" + String.format("%04X", codePoint) + "): " + e.getMessage());
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
        } catch (NullPointerException e) {
            System.err.println("[OFD-WARN] Font subsetting failed (NullPointerException) for " + fontFilePath + ": " + e.getMessage());
            System.err.println("[OFD-WARN] Falling back to full font embed");
            long fileSizeMB = new File(fontFilePath).length() / (1024 * 1024);
            if (fileSizeMB > 2) {
                System.err.println("[OFD-WARN] Full font embed: " + fontFilePath + " is " + fileSizeMB + " MB, consider using a compatible TTF");
            }
            return Path.of(fontFilePath);
        } catch (IllegalArgumentException e) {
            System.err.println("[OFD-WARN] Font subsetting failed (IllegalArgumentException) for " + fontFilePath + ": " + e.getMessage());
            System.err.println("[OFD-WARN] Falling back to full font embed");
            long fileSizeMB = new File(fontFilePath).length() / (1024 * 1024);
            if (fileSizeMB > 2) {
                System.err.println("[OFD-WARN] Full font embed: " + fontFilePath + " is " + fileSizeMB + " MB, consider using a compatible TTF");
            }
            return Path.of(fontFilePath);
        } catch (Exception e) {
            System.err.println("[OFD-WARN] Font subsetting failed for " + fontFilePath + ": " + e.getMessage());
            System.err.println("[OFD-WARN] Falling back to full font embed");
            long fileSizeMB = new File(fontFilePath).length() / (1024 * 1024);
            if (fileSizeMB > 2) {
                System.err.println("[OFD-WARN] Full font embed: " + fontFilePath + " is " + fileSizeMB + " MB, consider using a compatible TTF");
            }
            return Path.of(fontFilePath);
        }
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

        // Metadata 檢查：使用 PDFBox TrueTypeFont parser 讀取 PostScript Name
        // 如果為 null 則指派臨時名稱，避開 ofdrw NPE
        TTFParser parser = new TTFParser();
        TrueTypeFont ttf = parser.parse(new File(fontFilePath));
        String postScriptName = ttf.getNaming().getPostScriptName();

        String fontName;
        String fontFileName = new File(fontFilePath).getName();

        if (postScriptName == null || postScriptName.isEmpty()) {
            // PostScript Name 為 null，指派臨時名稱避開 ofdrw NPE
            fontName = "FallbackFont";
            System.out.println("  [OFD] WARNING: Font has null PostScript name, using temporary name: " + fontName);
        } else {
            fontName = fontFileName.replace(".ttf", "").replace(".otf", "");
        }
        ttf.close();

        Font ofdFont = new Font(fontName, fontName, Path.of(fontFilePath));
        ofdFont.setEmbeddable(true);
        ofdDoc.getResManager().addFont(ofdFont);
        System.out.println("  [OFD] Font embedded (full): " + fontFilePath);
        return ofdFont;
    }
    
    /**
     * 註冊字型到 OFD 文檔（嵌入子集化字型）
     * @return 包含 Font 和 subsetPath 的陣列，讓呼叫者負責刪除 subsetPath
     */
    private Object[] registerSubsetFont(OFDDoc ofdDoc, Set<Integer> usedChars) throws Exception {
        String fontFilePath = findFontFilePath();
        if (fontFilePath == null) {
            System.out.println("  [OFD] WARNING: No font file found, using system default (not embedded)");
            return new Object[]{null, null};
        }

        System.out.println("  [OFD] Subsetting font for " + usedChars.size() + " characters...");

        Path subsetPath = null;
        Path originalFontPath = null;
        try {
            subsetPath = subsetFont(fontFilePath, usedChars);
            // 檢查是否是原始路徑（fallback case）
            if (subsetPath.toString().equals(fontFilePath)) {
                // Fallback case: 子集化失敗，使用完整字體
                System.out.println("  [OFD] Using full font embed (subsetting failed)");
                originalFontPath = Path.of(fontFilePath);

                // Metadata 檢查
                TTFParser parser = new TTFParser();
                TrueTypeFont ttf = parser.parse(originalFontPath.toFile());
                String postScriptName = ttf.getNaming().getPostScriptName();
                ttf.close();

                String fontFileName = new File(fontFilePath).getName();
                String fontName;

                if (postScriptName == null || postScriptName.isEmpty()) {
                    // PostScript Name 為 null，指派臨時名稱避開 ofdrw NPE
                    fontName = "FallbackFont";
                    System.out.println("  [OFD] WARNING: Font has null PostScript name, using temporary name: " + fontName);
                } else {
                    fontName = fontFileName.replace(".ttf", "").replace(".otf", "");
                }

                Font ofdFont = new Font(fontName, fontName, originalFontPath);
                ofdFont.setEmbeddable(true);
                ofdDoc.getResManager().addFont(ofdFont);
                System.out.println("  [OFD] Full font registered successfully: " + fontName);
                // 返回 Font 和 null（不需要刪除原始字體檔案）
                return new Object[]{ofdFont, null};
            } else {
                // 正常子集化成功
                // Metadata 檢查
                TTFParser parser = new TTFParser();
                TrueTypeFont ttf = parser.parse(subsetPath.toFile());
                String postScriptName = ttf.getNaming().getPostScriptName();
                ttf.close();

                String fontFileName = new File(fontFilePath).getName();
                String fontName;

                if (postScriptName == null || postScriptName.isEmpty()) {
                    // PostScript Name 為 null，指派臨時名稱避開 ofdrw NPE
                    fontName = "FallbackFont";
                    System.out.println("  [OFD] WARNING: Subsetted font has null PostScript name, using temporary name: " + fontName);
                } else {
                    fontName = fontFileName.replace(".ttf", "").replace(".otf", "");
                }

                Font ofdFont = new Font(fontName, fontName, subsetPath);
                ofdFont.setEmbeddable(true);
                ofdDoc.getResManager().addFont(ofdFont);
                System.out.println("  [OFD] Font registered successfully: " + fontName);
                // 返回 Font 和 subsetPath，讓呼叫者在文檔寫入完成後刪除
                return new Object[]{ofdFont, subsetPath};
            }
        } catch (Exception e) {
            System.err.println("  [OFD] ERROR: Failed to register font: " + e.getMessage());
            // 如果出錯，立即刪除 subsetPath（如果是臨時檔案）
            if (subsetPath != null && !subsetPath.toString().equals(fontFilePath)) {
                try { Files.deleteIfExists(subsetPath); } catch (Exception ignored) {}
            }
            throw e;
        }
    }
    
    /**
     * 生成多頁 OFD（含字型嵌入）
     */
    public void generateMultiPageOfd(List<BufferedImage> images, List<List<OcrService.TextBlock>> allTextBlocks, File outputFile) throws Exception {
        if (images.size() != allTextBlocks.size()) {
            throw new IllegalArgumentException("Images and text blocks count mismatch");
        }

        System.out.println("  [OFD] Starting multi-page OFD generation...");
        System.out.println("  [OFD] Pages to process: " + images.size());
        System.out.println("  [OFD] Output file: " + outputFile.getAbsolutePath());

        Path tempDir = Files.createTempDirectory("ofd_multipage_");
        List<Path> tempImages = new ArrayList<>();
        Path fontSubsetPath = null;
        OFDDoc ofdDoc = null;

        try {
            // Create OFDDoc ONCE before the loop (like PdfService)
            System.out.println("  [OFD] Creating OFDDoc...");
            ofdDoc = new OFDDoc(outputFile.toPath());
            System.out.println("  [OFD] OFDDoc created successfully");

            // Collect used characters for font subsetting
            Set<Integer> usedChars = collectUsedChars(allTextBlocks);
            System.out.println("  [OFD] Collected " + usedChars.size() + " unique characters across all pages");

            // Register font - now returns both Font and subsetPath
            Object[] fontResult = registerSubsetFont(ofdDoc, usedChars);
            Font ofdFont = (Font) fontResult[0];
            fontSubsetPath = (Path) fontResult[1];

            System.out.println("  [OFD] Processing pages...");
            // Process each page INSIDE the same OFDDoc
            for (int pageIndex = 0; pageIndex < images.size(); pageIndex++) {
                System.out.println("  [OFD] Processing page " + (pageIndex + 1) + "/" + images.size() + "...");

                BufferedImage image = images.get(pageIndex);
                List<OcrService.TextBlock> textBlocks = allTextBlocks.get(pageIndex);

                // Write temp image
                Path tempImage = tempDir.resolve("page_" + pageIndex + ".png");
                ImageIO.write(image, "PNG", tempImage.toFile());
                tempImages.add(tempImage);
                System.out.println("  [OFD] Page " + (pageIndex + 1) + " - Image written to temp: " + tempImage);

                // Calculate page dimensions
                double widthMm = image.getWidth() * 25.4 / 72.0;
                double heightMm = image.getHeight() * 25.4 / 72.0;

                // Create NEW virtual page for EACH page (like PDPage in PdfService)
                PageLayout pageLayout = new PageLayout(widthMm, heightMm);
                pageLayout.setMargin(0d);
                VirtualPage vPage = new VirtualPage(pageLayout);
                System.out.println("  [OFD] Page " + (pageIndex + 1) + " - VirtualPage created (" + widthMm + "x" + heightMm + "mm)");

                // Add image
                Img img = new Img(tempImage);
                img.setPosition(Position.Absolute).setX(0d).setY(0d).setWidth(widthMm).setHeight(heightMm);
                vPage.add(img);
                System.out.println("  [OFD] Page " + (pageIndex + 1) + " - Image added");

                // Add text layer
                System.out.println("  [OFD] Page " + (pageIndex + 1) + " - Adding text layer (" + textBlocks.size() + " blocks)...");
                addTextLayer(vPage, textBlocks, ofdFont, pageIndex);
                System.out.println("  [OFD] Page " + (pageIndex + 1) + " - Text layer added");

                // Add page to document (EACH page added to SAME ofdDoc)
                ofdDoc.addVPage(vPage);
                System.out.println("  [OFD] Page " + (pageIndex + 1) + " - Added to OFDDoc");
            }

            System.out.println("  [OFD] All pages processed, closing OFDDoc (this will write the file)...");
            // Close OFDDoc AFTER the loop (like document.save() in PdfService)
            ofdDoc.close();
            System.out.println("  [OFD] OFDDoc closed");

            // Verify output file was created
            if (outputFile.exists()) {
                System.out.println("  [OFD] SUCCESS: OFD file created (" + outputFile.length() / 1024 + " KB)");
            } else {
                System.err.println("  [OFD] ERROR: Output file does not exist!");
                throw new RuntimeException("OFD file was not created");
            }

        } catch (Exception e) {
            System.err.println("  [OFD] CRITICAL ERROR during OFD generation:");
            System.err.println("  [OFD] " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            System.out.println("  [OFD] Cleaning up temp files...");
            // Close OFDDoc if still open
            if (ofdDoc != null) {
                try {
                    ofdDoc.close();
                } catch (Exception e) {
                    System.err.println("  [OFD] Warning: Failed to close OFDDoc: " + e.getMessage());
                }
            }
            for (Path tempImage : tempImages) {
                try {
                    Files.deleteIfExists(tempImage);
                } catch (Exception e) {
                    System.err.println("  [OFD] Warning: Failed to delete temp image: " + tempImage);
                }
            }
            try {
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                System.err.println("  [OFD] Warning: Failed to delete temp dir: " + tempDir);
            }
            // 現在可以安全刪除字型子集檔案（OFDDoc 已寫入完成）
            if (fontSubsetPath != null) {
                try {
                    // 提醒 GC 回收以釋放可能的文件鎖
                    System.gc();
                    Thread.sleep(100);
                    Files.deleteIfExists(fontSubsetPath);
                    System.out.println("  [OFD] Temp font subset file deleted");
                } catch (Exception e) {
                    System.err.println("  [OFD] Warning: Failed to delete temp font subset: " + e.getMessage());
                    // 標記在 JVM 退出時再刪除
                    fontSubsetPath.toFile().deleteOnExit();
                }
            }
            System.out.println("  [OFD] Cleanup complete");
        }
    }
    
    /**
     * 生成單頁 OFD（含字型嵌入）
     */
    public void generateOfd(BufferedImage image, List<OcrService.TextBlock> textBlocks, File outputFile) throws Exception {
        System.out.println("  [OFD] Starting single-page OFD generation...");
        System.out.println("  [OFD] Output file: " + outputFile.getAbsolutePath());

        Path tempDir = Files.createTempDirectory("ofd_");
        Path tempImage = tempDir.resolve("page.png");
        ImageIO.write(image, "PNG", tempImage.toFile());
        Path fontSubsetPath = null;  // 追蹤字型子集檔案

        try {
            try (OFDDoc ofdDoc = new OFDDoc(outputFile.toPath())) {
                System.out.println("  [OFD] OFDDoc created");

                // Register font - now returns both Font and subsetPath
                Object[] fontResult = registerSubsetFont(ofdDoc, collectUsedCharsSingle(textBlocks));
                Font ofdFont = (Font) fontResult[0];
                fontSubsetPath = (Path) fontResult[1];

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
                System.out.println("  [OFD] Page added to OFDDoc");
            }

            System.out.println("  [OFD] OFDDoc closed");

            // Verify output file was created
            if (outputFile.exists()) {
                System.out.println("  [OFD] SUCCESS: OFD file created (" + outputFile.length() / 1024 + " KB)");
            } else {
                System.err.println("  [OFD] ERROR: Output file does not exist!");
                throw new RuntimeException("OFD file was not created");
            }

        } catch (Exception e) {
            System.err.println("  [OFD] CRITICAL ERROR during OFD generation:");
            System.err.println("  [OFD] " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            // Clean up temp files
            try {
                Files.deleteIfExists(tempImage);
            } catch (Exception ignored) {}
            try {
                Files.deleteIfExists(tempDir);
            } catch (Exception ignored) {}
            // Delete font subset file after document is written
            if (fontSubsetPath != null) {
                try {
                    // 提醒 GC 回收以釋放可能的文件鎖
                    System.gc();
                    Thread.sleep(100);
                    Files.deleteIfExists(fontSubsetPath);
                    System.out.println("  [OFD] Temp font subset file deleted");
                } catch (Exception e) {
                    System.err.println("  [OFD] Warning: Failed to delete temp font subset: " + e.getMessage());
                    // 標記在 JVM 退出時再刪除
                    fontSubsetPath.toFile().deleteOnExit();
                }
            }
        }
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
