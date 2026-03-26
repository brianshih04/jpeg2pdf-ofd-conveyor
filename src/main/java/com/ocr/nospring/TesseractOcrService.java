package com.ocr.nospring;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.ITessAPI;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * Tesseract OCR 服務 - 僅用於希伯來文（PaddleOCR 不支援）
 */
public class TesseractOcrService {
    
    private final Tesseract tesseract;
    
    public TesseractOcrService(String dataPath, String language) throws Exception {
        tesseract = new Tesseract();
        if (dataPath != null && !dataPath.isEmpty()) {
            tesseract.setDatapath(dataPath);
        } else {
            // Default: look for tessdata next to the JAR/app
            String appDir = System.getProperty("user.dir");
            String defaultPath = appDir + "/tessdata";
            java.io.File f = new java.io.File(defaultPath);
            if (f.exists()) {
                tesseract.setDatapath(defaultPath);
            }
        }
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
    }
    
    public List<OcrService.TextBlock> recognize(BufferedImage image) {
        List<OcrService.TextBlock> textBlocks = new ArrayList<>();
        
        try {
            List<net.sourceforge.tess4j.Word> lines = tesseract.getWords(image, 
                ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
            
            if (lines == null || lines.isEmpty()) {
                return textBlocks;
            }
            
            for (net.sourceforge.tess4j.Word line : lines) {
                String text = line.getText();
                if (text != null && !text.trim().isEmpty()) {
                    java.awt.Rectangle rect = line.getBoundingBox();
                    
                    OcrService.TextBlock tb = new OcrService.TextBlock();
                    tb.text = text.trim();
                    tb.x = rect.getX();
                    tb.y = rect.getY();
                    tb.width = rect.getWidth();
                    tb.height = rect.getHeight();
                    tb.confidence = line.getConfidence() / 100.0;
                    tb.fontSize = (float) tb.height;
                    textBlocks.add(tb);
                }
            }
        } catch (Exception e) {
            System.err.println("    Error in Tesseract recognize: " + e.getMessage());
        }
        
        return textBlocks;
    }
}
