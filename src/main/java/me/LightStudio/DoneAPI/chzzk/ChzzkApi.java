package me.LightStudio.DoneAPI.chzzk;

import me.LightStudio.DoneAPI.exception.DoneException;
import me.LightStudio.DoneAPI.exception.ExceptionCode;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ChzzkApi {

    public static String getChatChannelId(String id) {
        String requestURL = "https://api.chzzk.naver.com/polling/v2/channels/" + id + "/live-status";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .uri(URI.create(requestURL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(response.body());

                Object contentObj = jsonObject.get("content");

                if (contentObj == null) {
                    throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR); // content가 없으면 예외
                }

                JSONObject content = (JSONObject) contentObj;
                Object chatChannelIdObj = content.get("chatChannelId");

                if (chatChannelIdObj == null) {
                    throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR); // chatChannelId가 없으면 예외
                }

                return chatChannelIdObj.toString();
            } else {
                throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
            }
        } catch (Exception e) {
            throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
        }
    }

    public static boolean isLive(String id) {
        String requestURL = "https://api.chzzk.naver.com/polling/v2/channels/" + id + "/live-status";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .uri(URI.create(requestURL))
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(response.body());

                // "content" 키가 없거나 null이면 방송 중이 아님
                if (!jsonObject.containsKey("content") || jsonObject.get("content") == null) {
                    //Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ChzzkApi] API 응답에 'content' 키가 없거나 null입니다.");
                    return false;
                }

                JSONObject content = (JSONObject) jsonObject.get("content");

                // "status" 확인 (방송이 진행 중이면 "OPEN")
                Object statusObj = content.get("status");
                if (statusObj == null || !"OPEN".equalsIgnoreCase(statusObj.toString())) {
                    //Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ChzzkApi] 방송 상태가 'OPEN'이 아닙니다: " + statusObj);
                    return false;
                }

                // "livePollingStatusJson" 확인
                Object livePollingStatusJsonObj = content.get("livePollingStatusJson");
                if (livePollingStatusJsonObj != null) {
                    JSONObject livePollingStatus = (JSONObject) parser.parse(livePollingStatusJsonObj.toString());
                    Object isPublishingObj = livePollingStatus.get("isPublishing");

                    if (isPublishingObj instanceof Boolean && (Boolean) isPublishingObj) {
                        //Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ChzzkApi] 방송이 진행 중입니다 (isPublishing=true).");
                        return true;
                    } else {
                        //Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ChzzkApi] 방송이 열려있지만 isPublishing=false 입니다.");
                        return false;
                    }
                }

                // "livePollingStatusJson" 자체가 없으면 기본적으로 false
                //Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ChzzkApi] 'livePollingStatusJson'이 없습니다. 방송 상태를 정확히 확인할 수 없습니다.");
                return false;
            } else {
                // HTTP 응답이 200이 아니면 실패로 처리
                //Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ChzzkApi] API 요청 실패. 상태 코드: " + response.statusCode());
                return false;
            }
        } catch (Exception e) {
            // 요청 실패하거나 파싱 실패 등 모든 에러는 false 반환
            //Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ChzzkApi] 예외 발생: " + e.getMessage());
            return false;
        }
    }

    public static String getAccessToken(String chatChannelId) {
        String requestURL = "https://comm-api.game.naver.com/nng_main/v1/chats/access-token?channelId=" + chatChannelId + "&chatType=STREAMING";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .uri(URI.create(requestURL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(response.body());
                String accessToken = ((JSONObject) jsonObject.get("content")).get("accessToken").toString();
                String extraToken = ((JSONObject) jsonObject.get("content")).get("extraToken").toString();
                return accessToken + ";" + extraToken;
            } else {
                throw new DoneException(ExceptionCode.API_ACCESS_TOKEN_ERROR);
            }
        } catch (Exception e) {
            throw new DoneException(ExceptionCode.API_ACCESS_TOKEN_ERROR);
        }
    }
}
