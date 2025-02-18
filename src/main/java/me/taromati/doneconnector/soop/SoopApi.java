package me.taromati.doneconnector.soop;

import me.taromati.doneconnector.Logger;
import me.taromati.doneconnector.exception.DoneException;
import me.taromati.doneconnector.exception.ExceptionCode;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SoopApi {
    public static SoopLiveInfo getPlayerLive(String bjid) {
        String requestURL = String.format("https://live.sooplive.co.kr/afreeca/player_live_api.php?bjid=%s", bjid);

        try {
            HttpClient client = HttpClient.newHttpClient();
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("bid", bjid);
            bodyJson.put("type", "live");
            bodyJson.put("pwd", "");
            bodyJson.put("player_type", "html5");
            bodyJson.put("stream_type", "common");
            bodyJson.put("quality", "HD");
            bodyJson.put("mode", "landing");
            bodyJson.put("is_revive", "false");
            bodyJson.put("from_api", "0");

            Logger.debug("Request URL: " + requestURL + "\n" + "Request Body: " + bodyJson.toJSONString());

            HttpRequest request = HttpRequest.newBuilder()
                    .POST(ofFormData(bodyJson))
                    .uri(URI.create(requestURL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(response.body());

                if (!jsonObject.containsKey("CHANNEL")) {
                    Logger.debug("방송 데이터가 없음: " + response.body());
                    return null; // 방송이 없으면 null 반환
                }

                JSONObject channel = (JSONObject) jsonObject.get("CHANNEL");
                SoopLiveInfo soopLiveInfo = new SoopLiveInfo(
                        channel.get("CHDOMAIN").toString(),
                        channel.get("CHATNO").toString(),
                        channel.get("FTK").toString(),
                        channel.get("TITLE").toString(),
                        channel.get("BJID").toString(),
                        channel.get("BNO").toString(),
                        channel.get("CHIP").toString(),
                        String.valueOf(Integer.parseInt(channel.get("CHPT").toString()) + 1),
                        channel.get("CTIP").toString(),
                        channel.get("CTPT").toString(),
                        channel.get("GWIP").toString(),
                        channel.get("GWPT").toString()
                );

                Logger.debug(soopLiveInfo.toString());

                return soopLiveInfo;
            } else {
                throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
            }
        } catch (Exception e) {
            throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
        }
    }

    public static boolean isLive(String bjid) {
        try {
            SoopLiveInfo liveInfo = getPlayerLive(bjid);
            return liveInfo != null; // 방송이 있으면 true, 없으면 false
        } catch (Exception e) {
            //Logger.error("숲 방송 상태 확인 중 오류 발생: " + e.getMessage());
            return false;
        }
    }

    public static HttpRequest.BodyPublisher ofFormData(Map<Object, Object> data) {
        var builder = new StringBuilder();

        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }

            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }

        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }
}
