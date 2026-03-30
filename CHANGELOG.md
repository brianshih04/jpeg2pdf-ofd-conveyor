# Changelog

## [3.0.0] - 2026-03-30

### Added

- **I18n Java Properties 系統**
  - I18nManager.java - 單例 i18n 管理器（Singleton 模式）
  - messages_zh_TW.properties - 繁中翻譯（預設）
  - messages_zh_CN.properties - 簡中翻譯
  - messages_en.properties - 英文翻譯（降級）
  - src/main/resources/i18n/ - 語言包目錄

- **JavaScript i18n 整合**
  - index.html - 加入 data-i18n 屬性
  - index.html - loadI18nMessages() 函式（從 Java 橋取）
  - index.html - applyLanguage() 函式（語言切換）
  - index.html - 統一事件監聽器（移除重複）
  - index.html - 加入詳細 console log（語言切換偵錯）

- **Bug 修復**
  - 移除重複的 settingUiLanguage 事件監聽器
  - 改用 i18nMessages 統一全域變數
  - 移除直接參考 i18n 的程式碼（所有引用改為 i18nMessages）

### 技術細節

- **語言包格式**：Java Properties（.properties 檔）
- **載入路徑**：`/i18n/` + fileName
- **降級機制**：請求語言 → 當前 → 英文 → 繁中 → Key 本身

- **執行緒安全**：`ConcurrentHashMap` + `synchronized` 確保載入時不並發寫入衝突

- **Java-JS 橋樑**：`setupJavaBridge()` → `window.javaApp.setMember()` → `loadI18nMessages()` → `applyTranslations()`

### 檔案變更統計

| 檔案 | 變更 |
|------|------|
| I18nManager.java | +100 行 |
| messages_zh_TW.properties | 新增 |
| messages_zh_CN.properties | 新增 |
| messages_en.properties | 新增 |
| index.html | 修改 |

---

## [3.0.0] - 2026-03-30

### 修正

- **Bug**: 移除重複的 change 事件監聽器（統一為一個全域處理器）

- **效能**: 簡化 JavaScript 事件監聽，減少不必要的多餘 DOM 操作

---

**Note**: 專案已 commit 並 push 到 GitHub。語言切換功能現在正常運作。
