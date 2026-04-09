package org.example.weathercastbot.util;

import java.util.Map;

public class LocationResolverUtil {

    public static class LocationRef {
        private String county;
        private String town;
        private boolean valid;

        public LocationRef(String county, String town, boolean valid) {
            this.county = county;
            this.town = town;
            this.valid = valid;
        }

        public String getCounty() { return county; }
        public String getTown() { return town; }
        public boolean hasTown() { return town != null && !town.isEmpty(); }
        public boolean isValid() { return valid; }
    }

    private static final Map<String, String[]> COUNTY_TOWNS = new java.util.LinkedHashMap<>();

    static {
        COUNTY_TOWNS.put("臺北市", new String[]{"中正區", "大同區", "中山區", "松山區", "大安區", "萬華區", "信義區", "士林區", "北投區", "內湖區", "南港區", "文山區"});
        COUNTY_TOWNS.put("新北市", new String[]{"萬里區", "金山區", "板橋區", "汐止區", "深坑區", "石碇區", "瑞芳區", "平溪區", "雙溪區", "貢寮區", "新店區", "坪林區", "烏來區", "永和區", "中和區", "土城區", "三峽區", "樹林區", "鶯歌區", "三重區", "新莊區", "泰山區", "林口區", "蘆洲區", "五股區", "八里區", "淡水區", "三芝區", "石門區"});
        COUNTY_TOWNS.put("基隆市", new String[]{"仁愛區", "信義區", "中正區", "中山區", "安樂區", "暖暖區", "七堵區"});
        COUNTY_TOWNS.put("桃園市", new String[]{"中壢區", "平鎮區", "龍潭區", "楊梅區", "新屋區", "觀音區", "桃園區", "龜山區", "八德區", "大溪區", "復興區", "大園區", "蘆竹區"});
        COUNTY_TOWNS.put("新竹縣", new String[]{"竹北市", "竹東鎮", "新埔鎮", "關西鎮", "湖口鄉", "新豐鄉", "芎林鄉", "橫山鄉", "北埔鄉", "寶山鄉", "尖石鄉", "五峰鄉"});
        COUNTY_TOWNS.put("新竹市", new String[]{"東區", "北區", "香山區"});
        COUNTY_TOWNS.put("苗栗縣", new String[]{"苗栗市", "苑裡鎮", "通霄鎮", "竹南鎮", "頭份市", "後龍鎮", "卓蘭鎮", "大湖鄉", "公館鄉", "銅鑼鄉", "南庄鄉", "頭屋鄉", "三義鄉", "西湖鄉", "造橋鄉", "三灣鄉", "獅潭鄉", "泰安鄉"});
        COUNTY_TOWNS.put("臺中市", new String[]{"中區", "東區", "南區", "西區", "北區", "北屯區", "西屯區", "南屯區", "太平區", "大里區", "霧峰區", "烏日區", "豐原區", "后里區", "石岡區", "東勢區", "和平區", "新社區", "潭子區", "大雅區", "神岡區", "大肚區", "沙鹿區", "龍井區", "梧棲區", "清水區", "大甲區", "外埔區", "大安區"});
        COUNTY_TOWNS.put("南投縣", new String[]{"南投市", "埔里鎮", "草屯鎮", "竹山鎮", "集集鎮", "名間鄉", "鹿谷鄉", "中寮鄉", "魚池鄉", "國姓鄉", "水里鄉", "信義鄉", "仁愛鄉"});
        COUNTY_TOWNS.put("彰化縣", new String[]{"彰化市", "鹿港鎮", "和美鎮", "線西鄉", "伸港鄉", "福興鄉", "秀水鄉", "花壇鄉", "芬園鄉", "員林市", "溪湖鎮", "田中鎮", "大村鄉", "埔鹽鄉", "埔心鄉", "永靖鄉", "社頭鄉", "二水鄉", "北斗鎮", "二林鎮", "田尾鄉", "埤頭鄉", "芳苑鄉", "大城鄉", "竹塘鄉", "溪州鄉"});
        COUNTY_TOWNS.put("雲林縣", new String[]{"斗六市", "斗南鎮", "虎尾鎮", "西螺鎮", "土庫鎮", "北港鎮", "古坑鄉", "大埤鄉", "莿桐鄉", "林內鄉", "二崙鄉", "崙背鄉", "麥寮鄉", "東勢鄉", "褒忠鄉", "臺西鄉", "元長鄉", "四湖鄉", "口湖鄉", "水林鄉"});
        COUNTY_TOWNS.put("嘉義縣", new String[]{"太保市", "朴子市", "布袋鎮", "大林鎮", "民雄鄉", "溪口鄉", "新港鄉", "六腳鄉", "東石鄉", "義竹鄉", "鹿草鄉", "水上鄉", "中埔鄉", "竹崎鄉", "梅山鄉", "番路鄉", "大埔鄉", "阿里山鄉"});
        COUNTY_TOWNS.put("嘉義市", new String[]{"東區", "西區"});
        COUNTY_TOWNS.put("臺南市", new String[]{"新營區", "鹽水區", "白河區", "柳營區", "後壁區", "東山區", "麻豆區", "下營區", "六甲區", "官田區", "大內區", "佳里區", "學甲區", "西港區", "七股區", "將軍區", "北門區", "新化區", "善化區", "新市區", "安定區", "山上區", "玉井區", "楠西區", "南化區", "左鎮區", "仁德區", "歸仁區", "關廟區", "龍崎區", "永康區", "東區", "南區", "北區", "安南區", "安平區", "中西區"});
        COUNTY_TOWNS.put("高雄市", new String[]{"鹽埕區", "鼓山區", "左營區", "楠梓區", "三民區", "新興區", "前金區", "苓雅區", "前鎮區", "旗津區", "小港區", "鳳山區", "林園區", "大寮區", "大樹區", "大社區", "仁武區", "鳥松區", "岡山區", "橋頭區", "燕巢區", "田寮區", "阿蓮區", "路竹區", "湖內區", "茄萣區", "永安區", "彌陀區", "梓官區", "旗山區", "美濃區", "六龜區", "甲仙區", "杉林區", "內門區", "茂林區", "桃源區", "那瑪夏區"});
        COUNTY_TOWNS.put("屏東縣", new String[]{"屏東市", "潮州鎮", "東港鎮", "恆春鎮", "萬丹鄉", "長治鄉", "麟洛鄉", "九如鄉", "里港鄉", "鹽埔鄉", "高樹鄉", "萬巒鄉", "內埔鄉", "竹田鄉", "新埤鄉", "枋寮鄉", "新園鄉", "崁頂鄉", "林邊鄉", "南州鄉", "佳冬鄉", "琉球鄉", "車城鄉", "滿州鄉", "枋山鄉", "三地門鄉", "霧臺鄉", "瑪家鄉", "泰武鄉", "來義鄉", "春日鄉", "獅子鄉", "牡丹鄉", "牡丹鄉"});
        COUNTY_TOWNS.put("宜蘭縣", new String[]{"宜蘭市", "羅東鎮", "蘇澳鎮", "頭城鎮", "礁溪鄉", "壯圍鄉", "員山鄉", "冬山鄉", "五結鄉", "三星鄉", "大同鄉", "南澳鄉"});
        COUNTY_TOWNS.put("花蓮縣", new String[]{"花蓮市", "鳳林鎮", "玉里鎮", "新城鄉", "吉安鄉", "壽豐鄉", "光復鄉", "豐濱鄉", "瑞穗鄉", "富里鄉", "秀林鄉", "萬榮鄉", "卓溪鄉"});
        COUNTY_TOWNS.put("臺東縣", new String[]{"臺東市", "成功鎮", "關山鎮", "卑南鄉", "大武鄉", "太麻里鄉", "東河鄉", "長濱鄉", "鹿野鄉", "池上鄉", "綠島鄉", "延平鄉", "海端鄉", "達仁鄉", "金峰鄉", "蘭嶼鄉"});
        COUNTY_TOWNS.put("澎湖縣", new String[]{"馬公市", "湖西鄉", "白沙鄉", "西嶼鄉", "望安鄉", "七美鄉"});
        COUNTY_TOWNS.put("金門縣", new String[]{"金城鎮", "金湖鎮", "金沙鎮", "金寧鄉", "烈嶼鄉", "烏坵鄉"});
        COUNTY_TOWNS.put("連江縣", new String[]{"南竿鄉", "北竿鄉", "莒光鄉", "東引鄉"});
    }

    public static LocationRef resolve(String input) {
        if (input == null || input.isBlank()) return new LocationRef("臺北市", null, true);
        String cleanInput = input.trim().replace("台", "臺");

        // 1. Direct exact county match
        if (COUNTY_TOWNS.containsKey(cleanInput)) {
            return new LocationRef(cleanInput, null, true);
        }

        // 2. Exact match for full town name (e.g. 信義區)
        for (Map.Entry<String, String[]> entry : COUNTY_TOWNS.entrySet()) {
            for (String town : entry.getValue()) {
                if (town.equals(cleanInput)) {
                    return new LocationRef(entry.getKey(), town, true);
                }
            }
        }

        // 3. Scan aliased counties and partial towns
        for (Map.Entry<String, String[]> entry : COUNTY_TOWNS.entrySet()) {
            String county = entry.getKey();
            
            if (county.startsWith(cleanInput)) {
                return new LocationRef(county, null, true);
            }

            for (String town : entry.getValue()) {
                if (town.startsWith(cleanInput) || cleanInput.contains(town)) {
                    return new LocationRef(county, town, true);
                }
            }
        }

        // 4. Complete fallback for unrecognized locations
        return new LocationRef(cleanInput, null, false);
    }

    /**
     * Scans a natural language sentence and extracts all matching Taiwan locations.
     * e.g., "請問信義區明天天氣" -> [LocationRef(臺北市, 信義區), LocationRef(基隆市, 信義區)]
     */
    public static java.util.List<LocationRef> parseLocationsFromText(String text) {
        java.util.List<LocationRef> matches = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return matches;
        String cleanText = text.replace("台", "臺");

        // Find all possible combinations
        for (Map.Entry<String, String[]> entry : COUNTY_TOWNS.entrySet()) {
            String countyKey = entry.getKey();
            String shortCounty = countyKey.substring(0, 2);
            boolean hasCounty = cleanText.contains(countyKey) || cleanText.contains(shortCounty);
            
            for (String town : entry.getValue()) {
                if (cleanText.contains(town)) {
                    matches.add(new LocationRef(countyKey, town, true));
                }
            }
            if (hasCounty && matches.stream().noneMatch(r -> r.getCounty().equals(countyKey))) {
                matches.add(new LocationRef(countyKey, null, true));
            }
        }
        
        // Exact matches found!
        if (!matches.isEmpty()) {
            boolean userSpecifiedAnyCounty = false;
            for (String county : COUNTY_TOWNS.keySet()) {
                if (cleanText.contains(county) || cleanText.contains(county.substring(0, 2))) {
                    userSpecifiedAnyCounty = true;
                    break;
                }
            }
            if (userSpecifiedAnyCounty) {
                 matches.removeIf(r -> !cleanText.contains(r.getCounty()) && !cleanText.contains(r.getCounty().substring(0, 2)));
            }
            
            java.util.List<LocationRef> finalMatches = new java.util.ArrayList<>();
            for (LocationRef r : matches) {
                if (!r.hasTown()) {
                    boolean hasTownInSameCounty = matches.stream().anyMatch(o -> o.hasTown() && o.getCounty().equals(r.getCounty()));
                    if (hasTownInSameCounty) continue;
                }
                finalMatches.add(r);
            }
            return finalMatches;
        }

        // Partial matching (fallback like '信義' instead of '信義區')
        for (Map.Entry<String, String[]> entry : COUNTY_TOWNS.entrySet()) {
            for (String town : entry.getValue()) {
                if (town.length() >= 3) {
                    String shortTown = town.substring(0, town.length() - 1);
                    if (cleanText.contains(shortTown)) {
                        matches.add(new LocationRef(entry.getKey(), town, true));
                    }
                }
            }
        }
        
        if(!matches.isEmpty()) {
            boolean userSpecifiedAnyCounty = false;
            for (String county : COUNTY_TOWNS.keySet()) {
                if (cleanText.contains(county) || cleanText.contains(county.substring(0, 2))) { 
                    userSpecifiedAnyCounty = true; 
                    break; 
                }
            }
            
            if (userSpecifiedAnyCounty) {
                 matches.removeIf(r -> !cleanText.contains(r.getCounty()) && !cleanText.contains(r.getCounty().substring(0, 2)));
            }
        }
        
        return matches;
    }
}
