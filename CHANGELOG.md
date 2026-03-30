# Changelog

All notable changes to this project will be documented in this file.
The format: [YYYY-MM-DD] - [Version]

## [3.0.0] - 2026-03-30

### Added
- **Tesseract tessdata**: 新增 24 個 Tesseract 語言包支援
  - Hebrew (heb), Thai (tha), Russian (rus), Arabic (ara), Ukrainian (uk), Bulgarian (bul), Serbian (srp), Macedonian (mkd), Belarusian (bel), Greek (ell), Hindi (hin), Gujarati (guj), Bengali (ben), Tamil (tam), Telugu (tel), Marathi (mar), Urdu (urd), Pashto (pus), Amharic (amh), Japanese (jpn), Korean (kor), Chinese Traditional (chi_tra), Chinese Simplified (chi_sim)
  - **Tesseract 語言包自動下載**: 透過 `OCRModelDownloader` 自動從 GitHub 下載 traineddata
  - **字體**: 新增 `wqy-ZenHei.ttf`（備援字體）到 `src/main/resources/fonts/`
  - **FontManager.java 重理**：移除未使用的 `PDF_FONT_MAP` 及相關 dead code（38 行）
  - **專案結構優化**：字體檔案從專案根目錄 `fonts/` 移至 `src/main/resources/fonts/`

### Changed
- **字體架構**：統一使用 GoNotoKurrent-Regular.ttf 作為主字體和 OFD 字體
  - **OCR 雙引擎策略**：chi_tra/chi_sim/eng 使用 RapidOCR，日韓及其他語系使用 Tesseract

  - **字體分流策略**：全域主字體統一為 GoNotoKurrent-Regular.ttf，備援字體為 wqy-ZenHei.ttf
