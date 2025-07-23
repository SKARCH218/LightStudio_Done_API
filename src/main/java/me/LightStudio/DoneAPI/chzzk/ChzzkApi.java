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
import java.util.concurrent.CompletableFuture;

public class ChzzkApi {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static CompletableFuture<String> getChatChannelId(String id) {
        String requestURL = "https://api.chzzk.naver.com/polling/v2/channels/" + id + "/live-status";

        HttpRequest request = HttpRequest.newBuilder()
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(requestURL))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JSONParser parser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) parser.parse(response.body());

                            Object contentObj = jsonObject.get("content");

                            if (contentObj == null) {
                                throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
                            }

                            JSONObject content = (JSONObject) contentObj;
                            Object chatChannelIdObj = content.get("chatChannelId");

                            if (chatChannelIdObj == null) {
                                throw new DoneException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
                            }

                            return chatChannelIdObj.toString();
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

    public static CompletableFuture<Boolean> isLive(String id) {
        String requestURL = "https://api.chzzk.naver.com/polling/v2/channels/" + id + "/live-status";

        HttpClient client = HTTP_CLIENT;
        HttpRequest request = HttpRequest.newBuilder()
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(requestURL))
                .header("User-Agent", "Mozilla/5.0")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JSONParser parser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) parser.parse(response.body());

                            if (!jsonObject.containsKey("content") || jsonObject.get("content") == null) {
                                return false;
                            }

                            JSONObject content = (JSONObject) jsonObject.get("content");

                            Object statusObj = content.get("status");
                            if (statusObj == null || !"OPEN".equalsIgnoreCase(statusObj.toString())) {
                                return false;
                            }

                            Object livePollingStatusJsonObj = content.get("livePollingStatusJson");
                            if (livePollingStatusJsonObj != null) {
                                JSONObject livePollingStatus = (JSONObject) parser.parse(livePollingStatusJsonObj.toString());
                                Object isPublishingObj = livePollingStatus.get("isPublishing");

                                if (isPublishingObj instanceof Boolean && (Boolean) isPublishingObj) {
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                            return false;
                        } catch (Exception e) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                })
                .exceptionally(e -> {
                    return false;
                });
    }

    public static CompletableFuture<String> getAccessToken(String chatChannelId) {
        String requestURL = "https://comm-api.game.naver.com/nng_main/v1/chats/access-token?channelId=" + chatChannelId + "&chatType=STREAMING";

        HttpClient client = HTTP_CLIENT;
        HttpRequest request = HttpRequest.newBuilder()
                .method("GET", HttpRequest.BodyPublishers.noBody())
                .uri(URI.create(requestURL))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JSONParser parser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) parser.parse(response.body());
                            String accessToken = ((JSONObject) jsonObject.get("content")).get("accessToken").toString();
                            String extraToken = ((JSONObject) jsonObject.get("content")).get("extraToken").toString();
                            return accessToken + ";" + extraToken;
                        } catch (Exception e) {
                            throw new DoneException(ExceptionCode.API_ACCESS_TOKEN_ERROR);
                        }
                    } else {
                        throw new DoneException(ExceptionCode.API_ACCESS_TOKEN_ERROR);
                    }
                })
                .exceptionally(e -> {
                    throw new DoneException(ExceptionCode.API_ACCESS_TOKEN_ERROR);
                });
    }
}
