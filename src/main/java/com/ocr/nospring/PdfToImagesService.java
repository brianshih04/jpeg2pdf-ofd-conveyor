package com.ocr.nospring;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 將 PDF 的每一頁渲染為圖片
 */
public class PdfToImagesService {

    /**
     * 將 PDF 每一頁渲染為 BufferedImage 列表
     * @param pdfFile PDF 檔案
     * @param dpi 渲染 DPI（預設 300）
     * @return 每頁的 BufferedImage
     */
    public List<BufferedImage> renderPages(File pdfFile, float dpi) throws IOException {
        List<BufferedImage> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            System.out.println("  PDF pages: " + pageCount + " (DPI: " + (int) dpi + ")");

            for (int i = 0; i < pageCount; i++) {
                System.out.println("  Rendering page " + (i + 1) + "/" + pageCount + "...");
                BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                pages.add(image);
            }
        }
        return pages;
    }
}
