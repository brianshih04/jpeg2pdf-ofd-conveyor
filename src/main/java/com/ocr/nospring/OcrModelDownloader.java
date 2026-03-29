package com.ocr.nospring;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * OCR 訓練資料自動下載器
 * 負責檢查並下載 Tesseract tessdata 檔案
 */
public class OcrModelDownloader {

    /**
     * Tesseract tessdata GitHub 原始檔案 URL 前綴
     */
    private static final String TESSDATA_BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/";

    /**
     * 檢查並確保 tessdata 檔案存在
     * 如果檔案不存在，會從 GitHub 自動下載
     *
     * @param tessdataPath tessdata 目錄路徑（例如 "C:/OCR/tessdata"）
     * @param langCode 語言代碼（例如 "eng", "chi_sim", "jpn"）
     * @return true 表示成功或已存在，false 表示失敗
     */
    public static boolean ensureTessdataExists(String tessdataPath, String langCode) {
        if (tessdataPath == null || tessdataPath.isEmpty()) {
            System.err.println("[OcrModelDownloader] ERROR: tessdataPath is null or empty");
            return false;
        }

        if (langCode == null || langCode.isEmpty()) {
            System.err.println("[OcrModelDownloader] ERROR: langCode is null or empty");
            return false;
        }

        // 構建檔案路徑
        Path tessdataDir = Paths.get(tessdataPath);
        Path traineddataFile = tessdataDir.resolve(langCode + ".traineddata");

        // 檢查檔案是否已存在
        if (Files.exists(traineddataFile)) {
            System.out.println("[OcrModelDownloader] Training data already exists: " + traineddataFile);
            return true;
        }

        // 建立目錄（如果不存在）
        try {
            if (!Files.exists(tessdataDir)) {
                Files.createDirectories(tessdataDir);
                System.out.println("[OcrModelDownloader] Created tessdata directory: " + tessdataDir);
            }
        } catch (IOException e) {
            System.err.println("[OcrModelDownloader] ERROR: Failed to create tessdata directory: " + e.getMessage());
            return false;
        }

        // 下載檔案
        System.out.println("[OcrModelDownloader] Downloading training data for: " + langCode);
        return downloadTraineddata(traineddataFile, langCode);
    }

    /**
     * 下載 Tesseract 訓練資料檔案
     *
     * @param outputFile 目標檔案路徑
     * @param langCode 語言代碼
     * @return true 表示下載成功
     */
    private static boolean downloadTraineddata(Path outputFile, String langCode) {
        String downloadUrl = TESSDATA_BASE_URL + langCode + ".traineddata";

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
                System.err.println("[OcrModelDownloader] ERROR: HTTP " + responseCode + " when downloading: " + downloadUrl);
                return false;
            }

            int contentLength = connection.getContentLength();
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(outputFile.toFile());

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytesRead = 0;
            int lastProgress = -1;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // 印出下載進度（每 10%）
                if (contentLength > 0) {
                    int progress = (int) ((totalBytesRead * 100) / contentLength);
                    if (progress / 10 > lastProgress / 10) {
                        lastProgress = progress;
                        System.out.printf("[OcrModelDownloader] Downloading: %d%% (%d / %d bytes)%n",
                                progress, totalBytesRead, contentLength);
                    }
                }
            }

            outputStream.flush();

            System.out.println("[OcrModelDownloader] Download completed: " + outputFile);
            System.out.println("[OcrModelDownloader] File size: " + (totalBytesRead / 1024) + " KB");
            return true;

        } catch (Exception e) {
            System.err.println("[OcrModelDownloader] ERROR: Failed to download training data: " + e.getMessage());

            // 刪除不完整的檔案
            if (Files.exists(outputFile)) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {}
            }

            return false;

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

    /**
     * 從多語言代碼字串中提取主要的語言代碼
     * 例如："jpn+chi_sim+eng" -> "eng"（返回第一個）
     *
     * @param langString 語言代碼字串
     * @return 主要語言代碼
     */
    public static String extractPrimaryLangCode(String langString) {
        if (langString == null || langString.isEmpty()) {
            return null;
        }

        // 處理 "+" 分隔的多語言
        String[] parts = langString.split("\\+");
        if (parts.length > 0) {
            return parts[0].trim();
        }

        return langString.trim();
    }
}
