package me.taromati.doneconnector.chzzk;

import me.taromati.doneconnector.exception.DoneException;
import me.taromati.doneconnector.exception.ExceptionCode;
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
                return ((JSONObject) jsonObject.get("content")).get("chatChannelId").toString();
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

                // "content" 키가 없으면 방송 중이 아님
                if (!jsonObject.containsKey("content")) {
                    //Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ChzzkApi] API 응답에 'content' 키가 없습니다.");
                    return false;
                }

                JSONObject content = (JSONObject) jsonObject.get("content");

                // "status" 확인 (방송이 진행 중이면 "OPEN")
                String status = (String) content.get("status");
                if (!"OPEN".equalsIgnoreCase(status)) {
                    //Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ChzzkApi] 방송 상태: " + status + " (방송이 열려있지 않음)");
                    return false;
                }

                // "livePollingStatusJson" 확인
                if (content.containsKey("livePollingStatusJson")) {
                    JSONObject livePollingStatus = (JSONObject) parser.parse((String) content.get("livePollingStatusJson"));
                    boolean isPublishing = (boolean) livePollingStatus.get("isPublishing");

                    if (isPublishing) {
                        //Bukkit.getConsoleSender().sendMessage(ChatColor.GREEN + "[ChzzkApi] 방송이 진행 중입니다.");
                        return true;
                    } else {
                        //Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ChzzkApi] 방송 상태: isPublishing = false (방송 중이 아님)");
                        return false;
                    }
                }

                Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[ChzzkApi] 'livePollingStatusJson'이 없습니다. 방송 상태를 확인할 수 없음.");
                return false;
            }
        } catch (Exception e) {
            //Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[ChzzkApi] API 요청 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
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
