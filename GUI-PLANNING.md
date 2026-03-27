# JPEG2PDF-OFD GUI 規劃文件

## 技術架構

- **JavaFX WebView**：原生視窗內嵌 Web UI，不外開瀏覽器
- **前端**：HTML + CSS (Tailwind) + JS，純靜態打包到 JAR
- **打包**：Conveyor（.exe / .app / .deb），使用者覺得是原生 app
- **啟動方式**：
  - `java -jar xxx.jar config.json` → CLI 模式（不變）
  - `java -jar xxx.jar --gui` → GUI 模式
- **Java↔JS 溝通**：WebEngine.executeScript() + JSObject

## 核心設計原則：GUI 是殼，CLI 是核心

GUI **不重複實作** OCR/PDF/OFD 邏輯，而是透過呼叫現有 CLI 的處理方法：

1. **收集使用者設定** — UI 收集語言、格式、路徑等參數
2. **組 JSON config** — 將 UI 設定轉為 Config 物件或 JSON
3. **呼叫現有邏輯** — 直接呼叫 `processMultiPage()`、`processPerPage()`、`processPdfToSearchable()` 等方法
4. **接收進度回報** — 透過 callback 更新 UI 進度條與狀態

```
GUI (WebView)
  ↓ 收集設定
  ↓ 組 Config
  ↓
Main.processMultiPage() / processPerPage() / processPdfToSearchable()
  ↓
OcrService / TesseractOcrService → PdfService / OfdService / TextService
  ↑
進度 callback → JS 更新 UI
```

**優點：**
- 後端零修改，GUI 只是另一個入口
- CLI 和 GUI 行為完全一致，不會有功能差異
- 測試只針對一組邏輯

## 依賴

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-web</artifactId>
    <version>21</version>
</dependency>
```

## 分支

- `master` — 主力 CLI 版本
- `master_ui` — GUI 實作分支（從 master 複製）
- `master_dual_cli` — 備份分支

---

## UI 結構：三個 Tab

### Tab 1: 轉換（主要操作）

```
┌─────────────────────────────────────┐
│  [轉換]  [設定]  [監看]             │
├─────────────────────────────────────┤
│  輸入類型: ○ 圖片資料夾  ○ PDF 檔案 │
│  輸入來源: [📁 選擇資料夾/檔案]     │
│            支援拖放資料夾/檔案      │
│  (PDF 模式)                         │
│    DPI: [300 ▼]  150/200/300/400   │
│  語言: [繁體中文 ▼]                 │
│  格式: ☑PDF ☑OFD ☑TXT             │
│  [🚀 開始轉換]                      │
│  進度: ████████░░ 80%              │
│  輸出: P:/OCR/Output/              │
│  ✅ 完成 15/20 頁                   │
└─────────────────────────────────────┘
```

#### 輸入類型說明

| 類型 | 說明 | 對應 config |
|------|------|-------------|
| 圖片資料夾 | 選擇資料夾，自動掃描 JPG/PNG | `input.type` 不設定（預設） |
| PDF 檔案 | 選擇單一或多個 PDF 檔案 | `input.type: "pdf"` + `input.dpi` |

- **PDF 模式**：選擇 PDF 檔案後，自動渲染每頁為圖片 → OCR → 產生可搜索的 PDF/OFD/TXT
- **DPI 選項**：PDF 渲染解析度，預設 300，越高品質越好但越慢

### Tab 2: 設定

```
┌─────────────────────────────────────┐
│  [轉換]  [設定]  [監看]             │
├─────────────────────────────────────┤
│                                     │
│  ▸ OCR 引擎                         │
│    引擎: [自動 ▼]                   │
│          自動 / RapidOCR / Tesseract│
│    Tesseract 資料路徑: [📁 選擇]   │
│                                     │
│  ▸ 輸出                             │
│    預設輸出資料夾: [📁 選擇]        │
│    檔名格式: [multipage_日期時間 ▼] │
│          multipage_YYYYMMDD_HHmmss  │
│          input_日期時間             │
│          {自訂格式}                 │
│    多頁模式: [合併 ○ / 逐頁 ○]     │
│                                     │
│  ▸ 字體                             │
│    字體: [自動（依語言） ▼]         │
│    自訂字體路徑: [📁 選擇]         │
│                                     │
│  ▸ 文字層                           │
│    顏色: [白色 ▼]                   │
│    透明度: ─────●── 0.0001         │
│                                     │
│  ▸ 文字轉換                         │
│    簡繁轉換: [不轉換 ▼]             │
│          不轉換 / 簡→繁 / 繁→簡    │
│                                     │
│  ▸ 界面                             │
│    UI 語言: [繁體中文 ▼]            │
│    啟動時最小化到系統托盤: ☐        │
│    完成後通知: ☑                    │
│    完成後自動開啟資料夾: ☐          │
│                                     │
│  [恢復預設]          [儲存設定]     │
└─────────────────────────────────────┘
```

#### 設定項目清單

| 分類 | 項目 | 預設值 | 說明 |
|------|------|--------|------|
| OCR | engine | auto | auto / rapidocr / tesseract |
| | tesseractDataPath | 空 | tessdata 目錄路徑 |
| 輸出 | defaultOutputFolder | 與輸入同目錄 | 預設輸出路徑 |
| | filenameFormat | multipage_YYYYMMDD_HHmmss | 檔名命名格式 |
| | multiPage | true | 合併/逐頁 |
| 字體 | fontMode | auto | auto(依語言) / custom |
| | customFontPath | 空 | 自訂字體路徑 |
| 文字層 | color | white | white/debug/red/black |
| | opacity | 0.0001 | 0.0 - 1.0 |
| 文字轉換 | textConvert | null | null / s2t / t2s |
| 介面 | uiLanguage | zh-TW | UI 顯示語言（見下表） |
| | minimizeToTray | false | 啟動時最小化到托盤 |
| | notifyOnComplete | true | 完成後系統通知 |
| | openFolderOnComplete | false | 完成後開啟資料夾 |

### Tab 3: 監看資料夾 (Watch Folder)

```
┌─────────────────────────────────────┐
│  [轉換]  [設定]  [監看]             │
├─────────────────────────────────────┤
│                                     │
│  ▸ 監看設定                         │
│    監看資料夾: [📁 選擇]           │
│    狀態: 🟢 監看中 / ⚫ 已停止     │
│    [▶ 開始監看]  [⏹ 停止監看]      │
│                                     │
│  ▸ 處理規則                         │
│    輸出資料夾: [📁 選擇]            │
│    OCR 語言: [繁體中文 ▼]           │
│    支援格式: ☑圖片(JPG/PNG) ☑PDF   │
│    輸出格式: ☑PDF ☑OFD ☐TXT       │
│    多頁模式: [逐檔轉換 ▼]          │
│      逐檔轉換 / 合併為多頁         │
│                                     │
│  ▸ 進階選項                         │
│    處理後動作: [移至已完成 ▼]       │
│      不處理 / 移至已完成資料夾      │
│      / 移至回收站 / 刪除原檔        │
│    已完成資料夾: [📁 選擇]         │
│    排除副檔名: [*.tmp, *.bak]       │
│    最小等待秒數: [3] 秒             │
│      （檔案寫入後等待穩定再處理）   │
│    同批合併間隔: [5] 秒             │
│      （連續檔案間隔超過此時間則    │
│       視為新一批）                  │
│                                     │
│  ▸ 監看記錄                         │
│  ┌─────────────────────────────────┐│
│  │ 09:30 ✅ scan_001.jpg → PDF    ││
│  │ 09:31 ✅ scan_002.jpg → PDF    ││
│  │ 09:32 ✅ scan_003.jpg → PDF    ││
│  │ 09:45 ⏳ photo_004.jpg 處理中.. ││
│  └─────────────────────────────────┘│
│  [清除記錄]                         │
│                                     │
│  📊 統計：今日 47 檔 | 成功 46 | ❌1│
│                                     │
└─────────────────────────────────────┘
```

#### 監看設定項目

| 項目 | 說明 | 預設 |
|------|------|------|
| watchFolder | 要監看的路徑 | — |
| watchOutputFolder | 轉換後存放位置 | 同監看資料夾/output |
| watchLanguage | OCR 語言 | 跟設定 Tab 一致 |
| watchFormats | 輸出格式 | PDF |
| watchMultiPage | 逐檔/合併 | 逐檔轉換 |
| afterProcessAction | 處理後動作 | 不處理 |
| completedFolder | 已完成資料夾 | — |
| excludePatterns | 排除副檔名 | *.tmp, *.bak |
| stableWaitSec | 檔案穩定等待秒數 | 3 秒 |
| batchIntervalSec | 同批合併間隔 | 5 秒 |
| watchNotifyOnComplete | 完成通知 | 開 |
| autoStartWatching | 啟動時自動監看 | 關 |

#### 進階場景

1. **掃描機場景**：掃描器自動存到資料夾 → 自動轉 PDF → 移到已完成
2. **多資料夾監看**：可支援多個監看資料夾（擴充功能）
3. **上班/下班排程**：只在工作時間監看

---

## UI 語言支援（20 種）

| 代碼 | 顯示名稱 |
|------|---------|
| zh-TW | 繁體中文 |
| zh-CN | 简体中文 |
| en | English |
| ja | 日本語 |
| ko | 한국어 |
| fr | Français |
| de | Deutsch |
| es | Español |
| pt | Português |
| it | Italiano |
| nl | Nederlands |
| pl | Polski |
| cs | Čeština |
| ru | Русский |
| ar | العربية |
| th | ไทย |
| vi | Tiếng Việt |
| id | Bahasa Indonesia |
| hi | हिन्दी |
| tr | Türkçe |

- UI 語言獨立於 OCR 語言
- 實作用 i18n JSON 檔：`i18n/zh-TW.json`、`i18n/en.json` 等
- 啟動時根據設定載入對應語言包

---

## 設定持久化

- 儲存位置：`~/.jpeg2pdf-ofd/settings.json`（用戶家目錄）
- 首次啟動自動建立預設設定
- 修改後自動儲存

## 其他功能

- **拖放支援**：主畫面可直接拖放資料夾/檔案
- **歷史記錄**：記住最近 5 次的輸入/輸出路徑
- **系統托盤**：最小化到托盤，背景持續監看
- **快捷操作**：托盤右鍵 → 快速轉換最近資料夾
