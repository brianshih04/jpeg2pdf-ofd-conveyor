package com.ocr.nospring;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.*;

/**
 * 純 Java SE 主程序 - 無 Spring Boot
 * 
 * 支持兩種輸出模式：
 * 1. perPage (默認) - 每個圖片生成一個 PDF/OFD
 * 2. multiPage - 所有圖片合併成一個多頁 PDF/OFD
 */
public class Main {
    
    private static final String VERSION = "3.0.0 (No Spring Boot)";
    
    public static void main(String[] args) {
        // Check for --gui flag first
        for (String arg : args) {
            if ("--gui".equals(arg) || "-gui".equals(arg)) {
                // Launch GUI mode
                GuiApp.launchGui(args);
                return;
            }
        }

        try {
            System.setProperty("java.awt.headless", "true");

            if (args.length == 0) {
                printUsage();
                System.exit(0);
            }

            String configFile = args[0];

            if (configFile.equals("--help") || configFile.equals("-h")) {
                printUsage();
                System.exit(0);
            }

            if (configFile.equals("--version") || configFile.equals("-v")) {
                System.out.println("JPEG2PDF-OFD v" + VERSION);
                System.exit(0);
            }
            
            // 創建配置
            Config config = new Config();
            
            // 加載配置文件
            File file = new File(configFile);
            if (!file.exists()) {
                System.err.println("ERROR: Config file not found: " + configFile);
                System.exit(1);
            }
            
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> configMap = mapper.readValue(file, Map.class);
            
            System.out.println("========================================");
            System.out.println("  JPEG2PDF-OFD v" + VERSION);
            System.out.println("========================================");
            System.out.println();
            System.out.println("Config: " + configFile);
            System.out.println("OK: Config loaded");
            
            // 讀取字體配置
            Map<String, Object> fontConfig = (Map<String, Object>) configMap.get("font");
            if (fontConfig != null && fontConfig.containsKey("path")) {
                String fontPath = (String) fontConfig.get("path");
                config.setFontPath(fontPath);
                System.out.println("Font: " + fontPath);
            }
            
            // 讀取文字層配置
            Map<String, Object> textLayerConfig = (Map<String, Object>) configMap.get("textLayer");
            if (textLayerConfig != null) {
                // 顏色設定（支持顏色名稱或 RGB）
                if (textLayerConfig.containsKey("color")) {
                    String color = (String) textLayerConfig.get("color");
                    config.setTextLayerColor(color);
                }
                if (textLayerConfig.containsKey("red")) {
                    config.setTextLayerRed(((Number) textLayerConfig.get("red")).intValue());
                }
                if (textLayerConfig.containsKey("green")) {
                    config.setTextLayerGreen(((Number) textLayerConfig.get("green")).intValue());
                }
                if (textLayerConfig.containsKey("blue")) {
                    config.setTextLayerBlue(((Number) textLayerConfig.get("blue")).intValue());
                }
                
                // 透明度設定
                if (textLayerConfig.containsKey("opacity")) {
                    config.setTextLayerOpacity(((Number) textLayerConfig.get("opacity")).doubleValue());
                }
            }
            
            // 讀取簡繁轉換配置
            if (configMap.containsKey("textConvert")) {
                String textConvert = (String) configMap.get("textConvert");
                config.setTextConvert(textConvert);
                System.out.println("Text Convert: " + textConvert);
            }
            
            // 創建 ProcessingService 實例
            ProcessingService processingService = new ProcessingService(config);
            
            // 獲取輸入配置
            Map<String, Object> inputConfig = (Map<String, Object>) configMap.get("input");
            Map<String, Object> outputConfig = (Map<String, Object>) configMap.get("output");
            Map<String, Object> ocrConfig = (Map<String, Object>) configMap.get("ocr");
            
            // 檢查是否為 PDF 輸入模式（將 PDF 轉為 searchable）
            String inputType = "image"; // default
            if (inputConfig != null && inputConfig.containsKey("type")) {
                inputType = ((String) inputConfig.get("type")).toLowerCase();
            }

            // 獲取 DPI（PDF 渲染用，預設 300）
            float renderDpi = 300f;
            if (inputConfig != null && inputConfig.containsKey("dpi")) {
                renderDpi = ((Number) inputConfig.get("dpi")).floatValue();
            }

            List<File> inputFiles;

            if ("pdf".equals(inputType)) {
                // PDF 輸入模式：提取輸入的 PDF 檔案
                inputFiles = getInputFiles(inputConfig);
                if (inputFiles.isEmpty()) {
                    System.err.println("ERROR: No PDF files found");
                    return;
                }
                System.out.println("Mode: PDF to Searchable");
                System.out.println("Found " + inputFiles.size() + " PDF file(s)");
                // 將在後面逐個渲染處理
            } else {
                // 原有圖片輸入模式
                inputFiles = getInputFiles(inputConfig);
                System.out.println("Found " + inputFiles.size() + " file(s)");
            }

            if (inputFiles.isEmpty()) {
                System.err.println("ERROR: No files found");
                return;
            }
            
            // 獲取輸出配置
            String outputFolder = getOutputFolder(outputConfig);
            String format = getOutputFormat(outputConfig);
            String language = getOcrLanguage(ocrConfig);
            boolean multiPage = getMultiPageMode(outputConfig);

            // 讀取 OCR 引擎選擇："auto"(預設), "rapidocr", "tesseract"
            String ocrEngine = "auto";
            if (ocrConfig != null && ocrConfig.containsKey("engine")) {
                ocrEngine = ((String) ocrConfig.get("engine")).toLowerCase();
            }
            System.out.println("OCR Engine: " + ocrEngine);

            // Read Tesseract tessdata path (only needed for Hebrew)
            if (ocrConfig != null && ocrConfig.containsKey("tesseractDataPath")) {
                config.setTesseractDataPath((String) ocrConfig.get("tesseractDataPath"));
            }
            
            // 創建輸出目錄
            File outputDir = new File(outputFolder);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            System.out.println("Output: " + outputFolder);
            System.out.println("Mode: " + (multiPage ? "Multi-Page" : "Per-Page"));
            System.out.println();
            
            // Create CLI progress callback
            ProcessingService.ProgressCallback callback = new ProcessingService.ProgressCallback() {
                @Override
                public void onProgress(int current, int total, String message) {
                    // Already printed by ProcessingService
                }

                @Override
                public void onComplete(List<String> outputFiles) {
                    // Summary already printed by ProcessingService
                }

                @Override
                public void onError(String message) {
                    System.err.println("ERROR: " + message);
                }
            };

            if ("pdf".equals(inputType)) {
                // PDF 轉 searchable 模式
                processingService.processPdfToSearchable(inputFiles, outputDir, format, language, ocrEngine, renderDpi, callback);
            } else if (multiPage) {
                // 多頁模式：所有圖片合併成一個 PDF/OFD
                processingService.processMultiPage(inputFiles, outputDir, format, language, ocrEngine, callback);
            } else {
                // 單頁模式：每個圖片一個 PDF/OFD
                processingService.processPerPage(inputFiles, outputDir, format, language, ocrEngine, callback);
            }
            
            System.out.println("========================================");
            System.out.println("  Done!");
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<File> getInputFiles(Map<String, Object> inputConfig) {
        List<File> files = new ArrayList<>();
        
        if (inputConfig == null) return files;
        
        if (inputConfig.containsKey("file")) {
            String filePath = (String) inputConfig.get("file");
            File file = new File(filePath);
            if (file.exists()) files.add(file);
            return files;
        }
        
        if (inputConfig.containsKey("folder")) {
            String folderPath = (String) inputConfig.get("folder");
            String pattern = inputConfig.containsKey("pattern") 
                ? (String) inputConfig.get("pattern") 
                : "*.jpg";
            
            File folder = new File(folderPath);
            if (folder.exists() && folder.isDirectory()) {
                findFiles(folder, pattern, files);
            }
        }
        
        return files;
    }
    
    private static void findFiles(File folder, String pattern, List<File> files) {
        File[] fileList = folder.listFiles();
        if (fileList == null) return;
        
        for (File file : fileList) {
            if (file.isDirectory()) {
                findFiles(file, pattern, files);
            } else if (file.isFile() && matchesPattern(file.getName(), pattern)) {
                files.add(file);
            }
        }
    }
    
    private static boolean matchesPattern(String filename, String pattern) {
        if (pattern.equals("*") || pattern.equals("*.*")) return true;
        if (pattern.startsWith("*.")) {
            String ext = pattern.substring(1).toLowerCase();
            return filename.toLowerCase().endsWith(ext);
        }
        return filename.equals(pattern);
    }
    
    private static String getOutputFolder(Map<String, Object> outputConfig) {
        if (outputConfig != null && outputConfig.containsKey("folder")) {
            return (String) outputConfig.get("folder");
        }
        return ".";
    }
    
    private static String getOutputFormat(Map<String, Object> outputConfig) {
        if (outputConfig != null) {
            // 支持 "formats" 或 "format" 鍵
            Object format = null;
            if (outputConfig.containsKey("formats")) {
                format = outputConfig.get("formats");
            } else if (outputConfig.containsKey("format")) {
                format = outputConfig.get("format");
            }
            
            if (format != null) {
                if (format instanceof String) return (String) format;
                if (format instanceof List) return String.join(",", (List<String>) format);
            }
        }
        return "pdf";
    }
    
    private static String getOcrLanguage(Map<String, Object> ocrConfig) {
        if (ocrConfig != null && ocrConfig.containsKey("language")) {
            return (String) ocrConfig.get("language");
        }
        return "chinese_cht";
    }
    
    /**
     * 獲取多頁模式配置
     * 默認為 false（單頁模式）
     */
    private static boolean getMultiPageMode(Map<String, Object> outputConfig) {
        if (outputConfig != null && outputConfig.containsKey("multiPage")) {
            Object multiPage = outputConfig.get("multiPage");
            if (multiPage instanceof Boolean) {
                return (Boolean) multiPage;
            }
            if (multiPage instanceof String) {
                return Boolean.parseBoolean((String) multiPage);
            }
        }
        return false; // 默認為單頁模式
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  JPEG2PDF-OFD v" + VERSION);
        System.out.println("  Pure Java SE - No Spring Boot");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar jpeg2pdf-ofd-nospring.jar config.json");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --help     Show help");
        System.out.println("  --version  Show version");
        System.out.println("  --gui      Launch GUI mode");
        System.out.println();
        System.out.println("Config example (per-page mode):");
        System.out.println("  {");
        System.out.println("    \"input\": {");
        System.out.println("      \"folder\": \"C:/OCR/Watch\",");
        System.out.println("      \"pattern\": \"*.jpg\"");
        System.out.println("    },");
        System.out.println("    \"output\": {");
        System.out.println("      \"folder\": \"C:/OCR/Output\",");
        System.out.println("      \"format\": \"pdf\",");
        System.out.println("      \"multiPage\": false");
        System.out.println("    },");
        System.out.println("    \"ocr\": {");
        System.out.println("      \"language\": \"chinese_cht\"");
        System.out.println("    }");
        System.out.println("  }");
        System.out.println();
        System.out.println("Config example (multi-page mode):");
        System.out.println("  {");
        System.out.println("    \"input\": {");
        System.out.println("      \"folder\": \"C:/OCR/Watch\",");
        System.out.println("      \"pattern\": \"*.jpg\"");
        System.out.println("    },");
        System.out.println("    \"output\": {");
        System.out.println("      \"folder\": \"C:/OCR/Output\",");
        System.out.println("      \"format\": \"all\",");
        System.out.println("      \"multiPage\": true");
        System.out.println("    },");
        System.out.println("    \"ocr\": {");
        System.out.println("      \"language\": \"chinese_cht\"");
        System.out.println("    }");
        System.out.println("  }");
        System.out.println();
        System.out.println("Output modes:");
        System.out.println("  multiPage: false - Each image generates one PDF/OFD (default)");
        System.out.println("  multiPage: true  - All images merged into one multi-page PDF/OFD");
        System.out.println();
    }
}
