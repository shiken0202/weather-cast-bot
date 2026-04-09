# WeatherCastBot 專案架構報告書

## 1. 專案概述
`WeatherCastBot` 是一個基於 Java Spring Boot 開發的自動化天氣與地震資訊通知機器人。該系統透過定時任務（Scheduling）獲取外部天氣與地震數據，並利用 AI（Gemini）處理資訊，最終將精準的預報推送給訂閱用戶。

## 2. 技術棧 (Technology Stack)

### 後端框架與語言
- **Java**: 核心開發語言。
- **Spring Boot**: 應用框架，提供快速開發與部署能力。
- **Spring Data JPA**: 用於資料庫對象關係映射（ORM），管理訂閱者與位置資訊。
- **Lombok**: 透過 `@Data`, `@Builder`, `@Getter`, `@Setter` 等註解簡化 Java Bean 代碼。

### 外部整合
- **Google Gemini AI**: 透過 `GeminiResponseDto` 可知，系統整合了 Gemini AI 用於生成或處理自然語言文本。
- **天氣/地震 API**: 透過多個 DTO（如 `WeatherInfoDto`, `EarthquakeDto`）對接外部氣象數據源。

### 其他工具
- **Spring Scheduling**: 使用 `@EnableScheduling` 實現定時抓取數據的自動化任務。
- **Timezone Handling**: 專為 `Asia/Taipei` 時區優化（見 `TimeFormatUtil`）。

## 3. 專案目錄架構分析

專案採取典型的分層架構，確保關注點分離（Separation of Concerns）：

### 3.1 數據傳輸層 (`dto`)
負責定義與外部 API 交互的數據格式：
- `WeatherInfoDto`, `TownshipForecastDto`: 處理天氣預報數據。
- `EarthquakeDto`: 處理地震資訊。
- `GeminiResponseDto`: 處理 AI 回傳的 JSON 格式。

### 3.2 持久化層 (`entity`)
定義資料庫表結構：
- `Subscriber`: 儲存訂閱用戶資訊。
- `Location`: 儲存地理位置對應關係。
- `RainAlertBlock`: 儲存雨量警報區塊資訊。

### 3.3 工具類 (`util`)
提供共用邏輯：
- `LocationResolverUtil`: 負責將輸入的縣市/鄉鎮解析為系統可識別的格式。
- `TimeFormatUtil`: 將 ISO 格式的時間字串轉換為易讀的中文格式（例如：03/27(星期五) 18:00）。

### 3.4 響應封裝 (`response`)
- `ApiResponse`: 統一 API 的回傳格式，增加系統的穩定性與可擴展性。

## 4. 核心運作流程
1. **定時觸發**：`WeatherCastBotApplication` 啟動排程任務。
2. **數據獲取**：系統調用天氣與地震 API，將結果映射至 `dto` 物件。
3. **資訊處理**：
   - 使用 `LocationResolverUtil` 確定目標區域。
   - 使用 `TimeFormatUtil` 格式化時間。
   - 將原始數據傳送至 Gemini AI 進行文本優化（透過 `GeminiResponseDto`）。
4. **推送通知**：根據 `Subscriber` 實體中的訂閱設定，將處理後的資訊發送給用戶。

## 5. 總結
本專案是一個結構清晰的 Spring Boot 應用，有效地結合了**自動化排程**、**外部 API 整合**與**生成式 AI**，能夠提供高度客製化且人性化的天氣資訊服務。
