package com.ocr.nospring;

/**
 * Utility class for Tesseract language detection and configuration.
 * Provides static helper methods to determine OCR engine selection
 * and language mapping for various non-Latin scripts.
 */
public final class TesseractLanguageHelper {

    private TesseractLanguageHelper() {
        // Utility class, prevent instantiation
    }

    public static boolean isHebrew(String language) {
        return "he".equalsIgnoreCase(language) || "hebrew".equalsIgnoreCase(language);
    }

    public static boolean isThai(String language) {
        return "th".equalsIgnoreCase(language) || "tha".equalsIgnoreCase(language) || "thai".equalsIgnoreCase(language);
    }

    public static boolean isRussian(String language) {
        return "ru".equalsIgnoreCase(language) || "rus".equalsIgnoreCase(language) || "russian".equalsIgnoreCase(language);
    }

    public static boolean isPersian(String language) {
        return "fa".equalsIgnoreCase(language) || "fas".equalsIgnoreCase(language) || "persian".equalsIgnoreCase(language) || "farsi".equalsIgnoreCase(language);
    }

    public static boolean isArabic(String language) {
        return "ar".equalsIgnoreCase(language) || "ara".equalsIgnoreCase(language) || "arabic".equalsIgnoreCase(language);
    }

    public static boolean isUkrainian(String language) {
        return "uk".equalsIgnoreCase(language) || "ukr".equalsIgnoreCase(language) || "ukrainian".equalsIgnoreCase(language);
    }

    public static boolean isBulgarian(String language) {
        return "bg".equalsIgnoreCase(language) || "bul".equalsIgnoreCase(language) || "bulgarian".equalsIgnoreCase(language);
    }

    public static boolean isSerbian(String language) {
        return "sr".equalsIgnoreCase(language) || "srp".equalsIgnoreCase(language) || "serbian".equalsIgnoreCase(language);
    }

    public static boolean isMacedonian(String language) {
        return "mk".equalsIgnoreCase(language) || "mkd".equalsIgnoreCase(language) || "macedonian".equalsIgnoreCase(language);
    }

    public static boolean isBelarusian(String language) {
        return "be".equalsIgnoreCase(language) || "bel".equalsIgnoreCase(language) || "belarusian".equalsIgnoreCase(language);
    }

    public static boolean isGreek(String language) {
        return "el".equalsIgnoreCase(language) || "ell".equalsIgnoreCase(language) || "gre".equalsIgnoreCase(language) || "greek".equalsIgnoreCase(language) || "grc".equalsIgnoreCase(language);
    }

    public static boolean isHindi(String language) {
        return "hi".equalsIgnoreCase(language) || "hin".equalsIgnoreCase(language) || "hindi".equalsIgnoreCase(language);
    }

    public static boolean isGujarati(String language) {
        return "gu".equalsIgnoreCase(language) || "guj".equalsIgnoreCase(language) || "gujarati".equalsIgnoreCase(language);
    }

    public static boolean isBengali(String language) {
        return "bn".equalsIgnoreCase(language) || "ben".equalsIgnoreCase(language) || "bengali".equalsIgnoreCase(language);
    }

    public static boolean isTamil(String language) {
        return "ta".equalsIgnoreCase(language) || "tam".equalsIgnoreCase(language) || "tamil".equalsIgnoreCase(language);
    }

    public static boolean isTelugu(String language) {
        return "te".equalsIgnoreCase(language) || "tel".equalsIgnoreCase(language) || "telugu".equalsIgnoreCase(language);
    }

    public static boolean isMarathi(String language) {
        return "mr".equalsIgnoreCase(language) || "mar".equalsIgnoreCase(language) || "marathi".equalsIgnoreCase(language);
    }

    public static boolean isUrdu(String language) {
        return "ur".equalsIgnoreCase(language) || "urd".equalsIgnoreCase(language) || "urdu".equalsIgnoreCase(language);
    }

    public static boolean isPashto(String language) {
        return "ps".equalsIgnoreCase(language) || "pus".equalsIgnoreCase(language) || "pashto".equalsIgnoreCase(language);
    }

    public static boolean isAmharic(String language) {
        return "am".equalsIgnoreCase(language) || "amh".equalsIgnoreCase(language) || "amharic".equalsIgnoreCase(language);
    }

    public static boolean isJapanese(String language) {
        return "ja".equalsIgnoreCase(language) || "jpn".equalsIgnoreCase(language) || "japanese".equalsIgnoreCase(language);
    }

    public static boolean isChineseTraditional(String language) {
        return "zh-tw".equalsIgnoreCase(language) || "chi_tra".equalsIgnoreCase(language) || "chinese-traditional".equalsIgnoreCase(language) || "zh-tw".equalsIgnoreCase(language);
    }

    public static boolean isChineseSimplified(String language) {
        return "zh-cn".equalsIgnoreCase(language) || "chi_sim".equalsIgnoreCase(language) || "chinese-simplified".equalsIgnoreCase(language) || "zh-cn".equalsIgnoreCase(language);
    }

    public static boolean isKorean(String language) {
        return "ko".equalsIgnoreCase(language) || "kor".equalsIgnoreCase(language) || "korean".equalsIgnoreCase(language);
    }

    public static boolean useTesseract(String language) {
        return isHebrew(language) || isThai(language) || isRussian(language) || isPersian(language) || isArabic(language) || isUkrainian(language) || isBulgarian(language) || isSerbian(language) || isMacedonian(language) || isBelarusian(language) || isGreek(language) || isHindi(language) || isGujarati(language) || isBengali(language) || isTamil(language) || isTelugu(language) || isMarathi(language) || isUrdu(language) || isPashto(language) || isAmharic(language) || isJapanese(language) || isChineseTraditional(language) || isChineseSimplified(language);
    }

    /**
     * 根據語言自動選擇 OCR 引擎
     * - chi_tra, chinese_cht → rapidocr
     * - chi_sim, chinese_chs → rapidocr
     * - eng, english → rapidocr
     * - 其他所有語系 → tesseract
     *
     * @param language 語言代碼
     * @return "rapidocr" 或 "tesseract"
     */
    public static String getAutoEngine(String language) {
        // RapidOCR 支持的語言列表
        if ("chi_tra".equalsIgnoreCase(language) || "chinese_cht".equalsIgnoreCase(language) ||
            "chinese-traditional".equalsIgnoreCase(language) || "zh-tw".equalsIgnoreCase(language)) {
            return "rapidocr";
        }
        if ("chi_sim".equalsIgnoreCase(language) || "chinese_chs".equalsIgnoreCase(language) ||
            "chinese-simplified".equalsIgnoreCase(language) || "zh-cn".equalsIgnoreCase(language)) {
            return "rapidocr";
        }
        if ("eng".equalsIgnoreCase(language) || "english".equalsIgnoreCase(language)) {
            return "rapidocr";
        }
        // 其他所有語言使用 Tesseract
        return "tesseract";
    }

    /**
     * 判斷是否應該使用 Tesseract OCR 引擎
     *
     * @param engine 指定的引擎（null、空、"auto"、"tesseract"、"rapidocr"）
     * @param language 語言代碼
     * @return true 表示使用 Tesseract，false 表示使用 RapidOCR
     */
    public static boolean shouldUseTesseract(String engine, String language) {
        // 如果明確指定了引擎，尊重用戶選擇
        if ("tesseract".equals(engine)) return true;
        if ("rapidocr".equals(engine)) return false;

        // 如果 engine 是 null、空或 "auto"，根據語言自動選擇
        String autoEngine = getAutoEngine(language);
        return "tesseract".equals(autoEngine);
    }

    public static String getTesseractLanguage(String language) {
        if (isHebrew(language)) return "heb+eng";
        if (isThai(language)) return "tha+eng";
        if (isRussian(language)) return "rus+eng";
        if (isPersian(language)) return "ara+eng";
        if (isArabic(language)) return "ara+eng";
        if (isUkrainian(language)) return "ukr+eng";
        if (isBulgarian(language)) return "bul+eng";
        if (isSerbian(language)) return "srp+eng";
        if (isMacedonian(language)) return "mkd+eng";
        if (isBelarusian(language)) return "bel+eng";
        if (isGreek(language)) return "ell+eng";
        if (isHindi(language)) return "hin+eng";
        if (isGujarati(language)) return "guj+eng";
        if (isBengali(language)) return "ben+eng";
        if (isTamil(language)) return "tam+eng";
        if (isTelugu(language)) return "tel+eng";
        if (isMarathi(language)) return "mar+eng";
        if (isUrdu(language)) return "urd+eng";
        if (isPashto(language)) return "pus+eng";
        if (isAmharic(language)) return "amh+eng";
        if (isJapanese(language)) return "jpn+eng";
        if (isKorean(language)) return "kor+eng";
        if (isChineseTraditional(language)) return "chi_tra+eng";
        if (isChineseSimplified(language)) return "chi_sim+eng";
        return "eng";
    }

    public static String getTesseractLabel(String language) {
        if (isHebrew(language)) return "Hebrew";
        if (isThai(language)) return "Thai";
        if (isRussian(language)) return "Russian";
        if (isPersian(language)) return "Persian";
        if (isArabic(language)) return "Arabic";
        if (isUkrainian(language)) return "Ukrainian";
        if (isBulgarian(language)) return "Bulgarian";
        if (isSerbian(language)) return "Serbian";
        if (isMacedonian(language)) return "Macedonian";
        if (isBelarusian(language)) return "Belarusian";
        if (isGreek(language)) return "Greek";
        if (isHindi(language)) return "Hindi";
        if (isGujarati(language)) return "Gujarati";
        if (isBengali(language)) return "Bengali";
        if (isTamil(language)) return "Tamil";
        if (isTelugu(language)) return "Telugu";
        if (isMarathi(language)) return "Marathi";
        if (isUrdu(language)) return "Urdu";
        if (isPashto(language)) return "Pashto";
        if (isAmharic(language)) return "Amharic";
        if (isJapanese(language)) return "Japanese";
        if (isKorean(language)) return "Korean";
        if (isChineseTraditional(language)) return "Chinese Traditional";
        if (isChineseSimplified(language)) return "Chinese Simplified";
        return language;
    }
}
