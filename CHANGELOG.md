# Changelog

## [3.0.0] - 2026-03-30 (master_ui_test branch)

### 新增功能
- **雙 OCR 引擎架構**：RapidOCR（繁簡中英文）+ Tesseract（日韓及其他 21 語言）
- **自動引擎選擇**：根據 language 自動選擇最佳 OCR 引擎（TesseractLanguageHelper.getAutoEngine()）
- **PDF-to-Searchable**：input.type: 'pdf' 支援將現有 PDF 轉為可搜索版本
- **JavaFX WebView GUI**：三 Tab 界面（轉檔/設定/日誌），--gui 啟動
- **FontManager 字體路由**：雙軸架構 + 字體緩存（ConcurrentHashMap）
- **全域備援字體**：GoNotoKurrent → wqy-ZenHei 兩層 fallback
- **OFD 字體嵌入**：ofdrw Font API + TTFSubsetter 子集化
- **OFD 子集化防禦**：try-catch NPE/IAE，自動降級全字體嵌入
- **OcrModelDownloader**：自動下載 Tesseract traineddata from GitHub
- **PSM 3 預設**：Tesseract 自動頁面分析，適合表格和混合排版
- **OEM 1 LSTM**：強制使用 LSTM 模式
- **多語系 Tesseract 擴充**：塞爾維亞、馬其頓、白俄羅斯、古吉拉特
- **GUI 多選語言下拉**：checkbox-based，engine-aware filtering
- **tessDataPath 可配置**：默認 C:\OCR\tessdata

### 修復
- PDF 文字層：逐字符渲染，跳過缺失 glyph 而非丟棄整個 block
- OFD 多頁：字體 subset temp file 延遲刪除（gc+sleep+deleteOnExit）
- GUI browse button：移除 CountDownLatch 死鎖
- GUI alert() → showToast()：避免 JavaFX WebView 線程阻塞
- Config.setFontPath(null) 正確清除

### 測試結論
- 繁中+英文 → RapidOCR 勝出（精準度遠高）
- 日文+簡中 → Tesseract 勝出（RapidOCR 假名崩潰）
- 韓文+英文表格 → Tesseract PSM 3 可用（RapidOCR 亂碼）
- Tesseract PSM 6 → PSM 3：韓文辨識從完全亂碼提升到幾乎完美

### Git 分支策略
- master: CLI 穩定版
- master_ui: GUI 開發
- master_ui_test: GUI 測試（當前活躍開發分支）
- master_dual_cli: 備份

### 關鍵檔案
- FontManager.java: 字體路由引擎
- OcrModelDownloader.java: tessdata 自動下載
- TesseractOcrService.java: Tesseract 整合（OEM 1 + PSM 3）
- TesseractLanguageHelper.java: 語言映射 + 自動引擎選擇
- GuiApp.java: JavaFX WebView 橋接
- index.html: GUI 前端（嵌入 JAR）
- PdfToImagesService.java: PDF → images 渲染

### 待辦
- [ ] 96 頁壓力測試
- [ ] 跨機器 OFD 字體渲染驗證
- [ ] 字體打包策略（~30MB 嵌入 JAR vs 外部下載）
- [ ] 測試穩定後合併 master_ui_test → master_ui → master
