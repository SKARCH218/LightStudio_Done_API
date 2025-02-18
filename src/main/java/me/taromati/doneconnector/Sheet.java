package me.taromati.doneconnector;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Sheet {
    private String SHEET_ID;
    private String CSV_URL;
    private final String CSV_FILE_PATH = "plugins/LightStudio_Done_API/csv/data.csv"; // CSV 저장 경로

    private final Plugin plugin;
    private final File sheetConfigFile;
    private final FileConfiguration sheetConfig;
    private FileConfiguration config;
    private final File configFile;
    private final Map<String, Map<String, String>> playerData = new HashMap<>();

    public Sheet(Plugin plugin) {
        this.plugin = plugin;

        // config.yml 파일 경로 설정
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

        // Sheet.yml 로드
        this.sheetConfigFile = new File(plugin.getDataFolder(), "Sheet.yml");
        if (!sheetConfigFile.exists()) {
            plugin.saveResource("Sheet.yml", false);
        }
        this.sheetConfig = YamlConfiguration.loadConfiguration(sheetConfigFile);

        // SHEET_ID 불러오기
        loadSheetConfig();
    }

    private void loadSheetConfig() {
        this.SHEET_ID = sheetConfig.getString("google-sheet.sheet-id", "기본_SHEET_ID");
        this.CSV_URL = "https://docs.google.com/spreadsheets/d/" + SHEET_ID + "/gviz/tq?tqx=out:csv";

        // 기본 값 설정 (파일이 없을 경우)
        if (!sheetConfigFile.exists()) {
            sheetConfig.set("google-sheet.sheet-id", SHEET_ID);
            saveSheetConfig();
        }
    }

    private void saveSheetConfig() {
        try {
            sheetConfig.save(sheetConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Sheet] Sheet.yml 저장 중 오류 발생: " + e.getMessage());
        }
    }

    public void reloadSheetConfig() {
        try {
            sheetConfig.load(sheetConfigFile); // 기존 객체 유지하면서 새 데이터 로드
            loadSheetConfig();
            plugin.getLogger().info(ChatColor.GREEN + "[Sheet] Sheet.yml이 리로드되었습니다!");
        } catch (Exception e) {
            plugin.getLogger().severe("[Sheet] Sheet.yml을 다시 로드하는 중 오류 발생: " + e.getMessage());
        }
    }

    public void fetchAndSaveData() {
        try {
            // Google Sheets CSV 다운로드
            downloadCSV();

            // CSV 파일 읽기
            List<String[]> csvData = readCSV();
            if (csvData.isEmpty()) {
                plugin.getLogger().warning("[Sheet] CSV 데이터가 비어 있습니다.");
                return;
            }

            // 첫 번째 행(헤더) 추출
            String[] headers = csvData.get(0);

            // ★ config.yml 초기화 (기존 데이터 삭제)
            config = new YamlConfiguration(); // 새로운 빈 YAML 객체 생성

            // CSV 데이터를 HashMap 및 YAML에 저장
            for (int i = 1; i < csvData.size(); i++) {
                String[] row = csvData.get(i);
                if (row.length < headers.length) continue;

                Map<String, String> dataMap = new HashMap<>();
                String platform = "", name = "", id = "", nickname = "", toggle = "";

                for (int j = 0; j < headers.length; j++) {
                    String key = headers[j].trim();
                    String value = row[j].trim();

                    switch (key) {
                        case "이름" -> name = value;
                        case "마크닉네임" -> nickname = value;
                        case "플렛폼" -> platform = value;
                        case "식별자" -> id = value;
                        case "토글" -> toggle = value;
                    }

                    dataMap.put(key, value);
                }

                // "토글" 값이 "TRUE"인 데이터만 저장
                if (!toggle.equalsIgnoreCase("TRUE")) continue;

                if (!platform.isEmpty() && !name.isEmpty() && !id.isEmpty()) {
                    // YAML 저장
                    config.set(platform + "." + name + ".식별자", id);
                    config.set(platform + "." + name + ".마크닉네임", nickname);

                    // HashMap 저장
                    playerData.put(id, dataMap);
                }
            }

            // config.yml 저장
            saveConfigFile();
            plugin.getLogger().info(ChatColor.GREEN + "[Sheet] Google Sheets 데이터가 업데이트되었습니다!");

        } catch (Exception e) {
            plugin.getLogger().severe("[Sheet] Google Sheets 데이터 가져오기 중 오류 발생: " + e.getMessage());
        }
    }

    private void downloadCSV() {
        try {
            URL url = new URL(CSV_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "text/csv");

            if (conn.getResponseCode() != 200) {
                plugin.getLogger().severe("[Sheet] Google Sheets 요청 실패: 응답 코드 " + conn.getResponseCode());
                return;
            }

            // CSV 파일 저장
            File csvFile = new File(CSV_FILE_PATH);
            csvFile.getParentFile().mkdirs();

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(csvFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            plugin.getLogger().info(ChatColor.GREEN + "[Sheet] Google Sheets CSV 다운로드 완료!");
        } catch (Exception e) {
            plugin.getLogger().severe("[Sheet] CSV 다운로드 중 오류 발생: " + e.getMessage());
        }
    }

    private List<String[]> readCSV() {
        List<String[]> csvData = new ArrayList<>();
        File csvFile = new File(CSV_FILE_PATH);

        if (!csvFile.exists()) {
            plugin.getLogger().severe("[Sheet] CSV 파일을 찾을 수 없습니다: " + CSV_FILE_PATH);
            return csvData;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            Pattern pattern = Pattern.compile("\"([^\"]*)\"|([^,]+)"); // 정규식 패턴

            while ((line = br.readLine()) != null) {
                List<String> parsedData = new ArrayList<>();
                Matcher matcher = pattern.matcher(line); // 정규식 매칭

                while (matcher.find()) {
                    parsedData.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
                }

                csvData.add(parsedData.toArray(new String[0]));
            }
        } catch (IOException e) {
            plugin.getLogger().severe("[Sheet] CSV 파일 읽기 중 오류 발생: " + e.getMessage());
        }

        return csvData;
    }

    private void saveConfigFile() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[Sheet] config.yml 저장 중 오류 발생: " + e.getMessage());
        }
    }
}
