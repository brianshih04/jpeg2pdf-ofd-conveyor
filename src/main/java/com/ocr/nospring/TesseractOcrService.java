package com.ocr.nospring;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.ITessAPI;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tesseract OCR 服務 - 僅用於希伯來文(PaddleOCR 不支援)
 * 支援自動下載缺少的訓練資料檔案
 */
public class TesseractOcrService {

    private final Tesseract tesseract;

    /**
     * 下載鎖定機制 - 防止多線程同時下載同一語言的 traineddata
     */
    private static final ConcurrentHashMap<String, Object> downloadLocks = new ConcurrentHashMap<>();

    /**
     * Tesseract tessdata GitHub 原始檔案 URL 前綴（標準版）
     */
    private static final String TESSDATA_BASE_URL = "https://github.com/tesseract-ocr/tessdata/raw/main/";

    /**
     * 檢查並確保 traineddata 檔案存在
     * 如果檔案不存在，會從 GitHub 自動下載
     *
     * @param langCode 語言代碼（例如 "eng", "heb", "chi_sim"）
     * @param dataPath tessdata 目錄路徑
     * @throws IOException 如果下載失敗
     */
    private void ensureTraineddataExists(String langCode, String dataPath) throws IOException {
        String fileName = langCode + ".traineddata";
        File targetFile = new File(dataPath, fileName);

        // 檢查檔案是否已存在
        if (targetFile.exists()) {
            return; // 已下載
        }

        // 確保目錄存在
        if (!Files.exists(Paths.get(dataPath))) {
            Files.createDirectories(Paths.get(dataPath));
        }

        // 獲取或創建該語言的鎖定物件
        Object lock = downloadLocks.computeIfAbsent(langCode, k -> new Object());

        synchronized (lock) {
            // 雙重檢查 - 獲取鎖定後再次確認
            if (targetFile.exists()) {
                return; // 已被其他線程下載
            }

            // 下載檔案
            downloadTraineddata(langCode, targetFile);
        }
    }

    /**
     * 下載 Tesseract 訓練資料檔案
     *
     * @param langCode 語言代碼
     * @param targetFile 目標檔案
     * @throws IOException 如果下載失敗
     */
    private void downloadTraineddata(String langCode, File targetFile) throws IOException {
        String downloadUrl = TESSDATA_BASE_URL + langCode + ".traineddata";

        System.out.println("Downloading Tesseract traineddata: " + langCode + "...");

        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            URL url = new URL(downloadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000); // 30 秒連接超時
            connection.setReadTimeout(60000);    // 60 秒讀取超時

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download Tesseract traineddata for '" + langCode +
                    "' from GitHub. HTTP " + responseCode + ". Check network connection.");
            }

            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(targetFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            outputStream.flush();

            System.out.println("Downloaded Tesseract traineddata: " + langCode + " (" + (totalBytesRead / 1024) + " KB)");

        } catch (IOException e) {
            // 刪除不完整的檔案
            if (targetFile.exists()) {
                targetFile.delete();
            }
            throw new IOException("Failed to download Tesseract traineddata for '" + langCode +
                "' from GitHub. Check network connection.", e);
        } finally {
            // 關閉資源
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {}
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public TesseractOcrService(String dataPath, String language) throws Exception {
        tesseract = new Tesseract();

        // 處理 tessdata 路徑
        String effectiveDataPath;
        if (dataPath != null && !dataPath.isEmpty()) {
            effectiveDataPath = dataPath;
        } else {
            // Default: C:\OCR\tessdata\
            effectiveDataPath = "C:\\OCR\\tessdata";
        }

        // 確保目錄存在
        if (!Files.exists(Paths.get(effectiveDataPath))) {
            Files.createDirectories(Paths.get(effectiveDataPath));
        }

        // 處理多語言代碼（例如 "heb+eng"）
        String[] languages = language.split("\\+");
        for (String lang : languages) {
            String langCode = lang.trim();
            if (!langCode.isEmpty()) {
                // 確保該語言的 traineddata 檔案存在（自動下載）
                ensureTraineddataExists(langCode, effectiveDataPath);
            }
        }

        // 設定 datapath 和 language
        tesseract.setDatapath(effectiveDataPath);
        tesseract.setLanguage(language);

        // 效能優化:設定 OEM 為 LSTM 模式
        tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);

        // 效能優化:設定 PSM 為自動頁面分析(適合表格、混合排版)
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
