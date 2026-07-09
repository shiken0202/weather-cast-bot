package org.example.weathercastbot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TyphoonDto {
    private String typhoonName;    // 颱風名稱 (例如：凱米 GAEMI)
    private String warningType;    // 警報種類 (例如：海上陸上颱風警報)
    private String reportNumber;   // 報數 (例如：第 5 報)
    private String content;        // 警報具體內容 (警戒區域等)
    private String updateTime;     // 更新時間
}
