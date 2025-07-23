package me.LightStudio.DoneAPI.soop;

import me.LightStudio.DoneAPI.Logger;
import me.LightStudio.DoneAPI.exception.DoneException;
import me.LightStudio.DoneAPI.exception.ExceptionCode;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SoopApi {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static CompletableFuture<SoopLiveInfo> getPlayerLive(String bjid) {
        String requestURL = String.format("https://live.sooplive.co.kr/afreeca/player_live_api.php?bjid=%s", bjid);

        HttpClient client = HTTP_CLIENT;
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

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JSONParser parser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) parser.parse(response.body());

                            if (!jsonObject.containsKey("CHANNEL")) {
                                Logger.debug("방송 데이터가 없음: " + response.body());
                                return null;
                            }

                            JSONObject channel = (JSONObject) jsonObject.get("CHANNEL");
                            SoopLiveInfo soopLiveInfo = new SoopLiveInfo(
                                    getStringOrDefault(channel, "CHDOMAIN", ""),
                                    getStringOrDefault(channel, "CHATNO", ""),
                                    getStringOrDefault(channel, "FTK", ""),
                                    getStringOrDefault(channel, "TITLE", ""),
                                    getStringOrDefault(channel, "BJID", ""),
                                    getStringOrDefault(channel, "BNO", ""),
                                    getStringOrDefault(channel, "CHIP", ""),
                                    String.valueOf(getIntegerOrDefault(channel, "CHPT", 0) + 1),
                                    getStringOrDefault(channel, "CTIP", ""),
                                    getStringOrDefault(channel, "CTPT", ""),
                                    getStringOrDefault(channel, "GWIP", ""),
                                    getStringOrDefault(channel, "GWPT", "")
                            );

                            Logger.debug(soopLiveInfo.toString());

                            return soopLiveInfo;
                        } catch (Exception e) {
                            throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
                        }
                    } else {
                        throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
                    }
                })
                .exceptionally(e -> {
                    throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
                });
    }

    public static CompletableFuture<Boolean> isLive(String bjid) {
        return getPlayerLive(bjid).thenApply(liveInfo -> liveInfo != null);
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

    private static String getStringOrDefault(JSONObject jsonObject, String key, String defaultValue) {
        Object value = jsonObject.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private static int getIntegerOrDefault(JSONObject jsonObject, String key, int defaultValue) {
        Object value = jsonObject.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            Logger.warn("Failed to parse integer for key " + key + ": " + value + ", using default value " + defaultValue);
            return defaultValue;
        }
    }
}