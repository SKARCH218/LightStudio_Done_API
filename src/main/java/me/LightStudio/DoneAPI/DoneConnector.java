package me.LightStudio.DoneAPI;

import me.LightStudio.DoneAPI.chzzk.ChzzkApi;
import me.LightStudio.DoneAPI.chzzk.ChzzkWebSocket;
import me.LightStudio.DoneAPI.exception.DoneException;
import me.LightStudio.DoneAPI.exception.ExceptionCode;
import me.LightStudio.DoneAPI.soop.SoopApi;
import me.LightStudio.DoneAPI.soop.SoopLiveInfo;
import me.LightStudio.DoneAPI.soop.SoopWebSocket;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.protocols.Protocol;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.OfflinePlayer;
import java.io.File;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class DoneConnector extends JavaPlugin implements Listener {
    public static Plugin plugin;

    public static boolean debug;
    public static boolean random;
    public static boolean poong = false;

    private static final Map<String, Boolean> liveStatus = new HashMap<>();
    private static final List<Map<String, String>> chzzkUserList = new ArrayList<>();
    private static final List<Map<String, String>> soopUserList = new ArrayList<>();
    public static final HashMap<Integer, List<String>> donationRewards = new HashMap<>();
    List<ChzzkWebSocket> chzzkWebSocketList = new ArrayList<>();
    List<SoopWebSocket> soopWebSocketList = new ArrayList<>();

    private Sheet sheet; // Sheet 객체 선언

    @Override
    public void onEnable() {
        plugin = this;
        Bukkit.getPluginManager().registerEvents(this, this);

        // 명령어 등록 확인
        if (getCommand("done") == null) {
            Logger.error("명령어 'done'을 찾을 수 없습니다. plugin.yml을 확인하세요.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("done").setExecutor(this);
        getCommand("done").setTabCompleter(this);

        // 설정 파일 로드
        try {
            loadConfig();
        } catch (Exception e) {
            Logger.error("설정 파일을 불러오는 중 오류가 발생했습니다.");
            Logger.debug("오류 상세: " + e.getMessage());
            Logger.error("플러그인을 종료합니다.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 외부 데이터 연동
        loadUsersAndConnectAsync();

        // Sheet 객체 생성 후 데이터 가져오기 (비동기 실행)
        if (SheetMode){
            sheet = new Sheet(this);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> sheet.fetchAndSaveData()); // 비동기 실행
        }

        // 방송 상태 감지 스케줄러 실행
        startLiveStatusChecker();

        Logger.info(ChatColor.GREEN + "플러그인 활성화 완료.");
    }

    @Override
    public void onDisable() {
        disconnectChzzkList();
        disconnectSoopList();
        Logger.info(ChatColor.GREEN + "플러그인 비활성화 완료.");
    }

    private void clearConfig() {
        debug = false;
        random = false;
        chzzkUserList.clear();
        soopUserList.clear();
        donationRewards.clear();
        reloadConfig();
    }

    private boolean SheetMode; // Google Sheets 연동 모드
    private boolean markLive;  // 방송 명령어 실행 여부
    private boolean WhiteList;

    private void loadConfig() throws DoneException {
        this.saveDefaultConfig(); // config.yml 기본 파일 저장
        FileConfiguration config = this.getConfig();

        // 새 설정 추가 (config.yml에 없으면 기본값 설정)
        if (!config.contains("SheetMode")) {
            config.set("SheetMode", false);
        }
        if (!config.contains("MarkLive")) {
            config.set("MarkLive", false);
        }
        this.saveConfig(); // 변경된 내용 저장

        // 설정 값 불러오기
        SheetMode = config.getBoolean("SheetMode", false);
        markLive = config.getBoolean("MarkLive", false);
        WhiteList = config.getBoolean("WhiteList", false);

        // done.yml 로드
        File doneFile = new File(getDataFolder(), "done.yml");
        if (!doneFile.exists()) saveResource("done.yml", false);
        FileConfiguration doneConfig = YamlConfiguration.loadConfiguration(doneFile);

        try {
            debug = doneConfig.getBoolean("디버그", false);
            random = doneConfig.getBoolean("랜덤 보상", false);
            poong = doneConfig.getBoolean("숲풍선갯수로출력", false);

            // 후원 보상 로드
            Logger.info("후원 보상 로드 중...");
            ConfigurationSection donationSection = doneConfig.getConfigurationSection("후원 보상");

            if (donationSection != null) {
                for (String price : donationSection.getKeys(false)) {
                    donationRewards.put(Integer.parseInt(price), doneConfig.getStringList("후원 보상." + price));
                }
            }

            if (donationRewards.isEmpty()) {
                throw new DoneException(ExceptionCode.REWARD_NOT_FOUND);
            }

            Logger.info(ChatColor.GREEN + "후원 보상 목록 " + donationRewards.size() + "개 로드 완료.");
        } catch (Exception e) {
            throw new DoneException(ExceptionCode.REWARD_PARSE_ERROR);
        }

        Logger.info(ChatColor.GREEN + "설정 파일 로드 완료.");
    }


    // 유저 데이터 로드하는 공통 함수 추가
    private void loadUsers(FileConfiguration config, String section, List<Map<String, String>> userList) {
        if (config.getConfigurationSection(section) == null) return;

        Set<String> nicknameList = config.getConfigurationSection(section).getKeys(false);
        for (String nickname : nicknameList) {
            String id = config.getString(section + "." + nickname + ".식별자");
            String tag = config.getString(section + "." + nickname + ".마크닉네임");

            if (id == null || tag == null) continue;

            Map<String, String> userMap = new HashMap<>();
            userMap.put("nickname", nickname);
            userMap.put("id", id);
            userMap.put("tag", tag);
            userList.add(userMap);

            Logger.debug(section + " 유저: " + userMap);
        }
    }

    private void disconnectByNickName(String target) {
        chzzkWebSocketList = chzzkWebSocketList.stream()
                .filter(chzzkWebSocket -> {
                    if (Objects.equals(chzzkWebSocket.getChzzkUser().get("nickname"), target) ||
                            Objects.equals(chzzkWebSocket.getChzzkUser().get("tag"), target)) {

                        try {
                            chzzkWebSocket.close();
                        } catch (Exception e) {
                            Logger.warn("[Chzzk] 웹소켓 닫기 중 오류 발생: " + e.getMessage());
                        }


                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        soopWebSocketList = soopWebSocketList.stream()
                .filter(soopWebSocket -> {
                    if (Objects.equals(soopWebSocket.getSoopUser().get("nickname"), target) ||
                            Objects.equals(soopWebSocket.getSoopUser().get("tag"), target)) {

                        try {
                            soopWebSocket.close();
                        } catch (Exception e) {
                            Logger.warn("[SOOP] 웹소켓 닫기 중 오류 발생: " + e.getMessage());
                        }


                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private CompletableFuture<Boolean> isChzzkLive(String chzzkId) {
        return ChzzkApi.isLive(chzzkId)
                .exceptionally(e -> {
                    Logger.error("치지직 방송 상태 확인 중 오류 발생: " + e.getMessage());
                    return false;
                });
    }

    private void connectChzzk(Map<String, String> chzzkUser) {
        String chzzkId = chzzkUser.get("id");
        isChzzkLive(chzzkId).thenAccept(isLive -> {
            if (!isLive) {
                Logger.info("[Chzzk][" + chzzkUser.get("nickname") + "] 현재 방송이 진행 중이지 않음.");
                return;
            }

            ChzzkApi.getChatChannelId(chzzkId)
                    .thenCompose(chatChannelId -> ChzzkApi.getAccessToken(chatChannelId)
                            .thenAccept(token -> {
                                String accessToken = token.split(";")[0];
                                String extraToken = token.split(";")[1];

                                ChzzkWebSocket webSocket = new ChzzkWebSocket(
                                        "wss://kr-ss1.chat.naver.com/chat", chatChannelId, accessToken, extraToken, chzzkUser, donationRewards);
                                webSocket.connect();
                                chzzkWebSocketList.add(webSocket);
                                Logger.info("[Chzzk][" + chzzkUser.get("nickname") + "] 방송 연결 성공.");
                            })
                            .exceptionally(e -> {
                                Logger.error("[Chzzk][" + chzzkUser.get("nickname") + "] 액세스 토큰 가져오는 중 오류 발생.");
                                Logger.debug(e.getMessage());
                                return null;
                            })
                    )
                    .exceptionally(e -> {
                        Logger.error("[Chzzk][" + chzzkUser.get("nickname") + "] 채팅 채널 ID 가져오는 중 오류 발생.");
                        Logger.debug(e.getMessage());
                        return null;
                    });
        });
    }

    private void connectChzzkList() {
        for (Map<String, String> chzzkUser : chzzkUserList) {
            try {
                connectChzzk(chzzkUser);
            } catch (Exception e) {
                Logger.error("[Chzzk][" + chzzkUser.get("nickname") + "] 연결 중 오류 발생: " + e.getMessage());
            }
        }
    }


    private void disconnectChzzkList() {
        for (ChzzkWebSocket webSocket : chzzkWebSocketList) {
            webSocket.close();
        }

        chzzkWebSocketList.clear();
    }

    private CompletableFuture<Boolean> isSoopLive(String soopId) {
        return SoopApi.isLive(soopId)
                .exceptionally(e -> {
                    Logger.error("숲 방송 상태 확인 중 오류 발생: " + e.getMessage());
                    return false;
                });
    }

    private void connectSoop(Map<String, String> soopUser) {
        String soopId = soopUser.get("id");
        isSoopLive(soopId).thenAccept(isLive -> {
            if (!isLive) {
                Logger.info("[Soop][" + soopUser.get("nickname") + "] 현재 방송이 진행 중이지 않음.");
                return;
            }

            SoopApi.getPlayerLive(soopId)
                    .thenAccept(liveInfo -> {
                        if (liveInfo == null) {
                            Logger.info("[Soop][" + soopUser.get("nickname") + "] 라이브 정보 없음.");
                            return;
                        }
                        org.java_websocket.drafts.Draft_6455 draft6455 = new org.java_websocket.drafts.Draft_6455(
                                Collections.emptyList(),
                                Collections.singletonList(new org.java_websocket.protocols.Protocol("chat"))
                        );
                        SoopWebSocket webSocket = new SoopWebSocket(
                                "wss://" + liveInfo.CHDOMAIN() + ":" + liveInfo.CHPT() + "/Websocket/" + liveInfo.BJID(),
                                draft6455, liveInfo, soopUser, donationRewards, poong);
                        webSocket.connect();
                        soopWebSocketList.add(webSocket);
                        Logger.info("[Soop][" + soopUser.get("nickname") + "] 방송 연결 성공.");
                    })
                    .exceptionally(e -> {
                        Logger.error("[Soop][" + soopUser.get("nickname") + "] 숲 채팅 연결 중 오류 발생.");
                        Logger.debug(e.getMessage());
                        return null;
                    });
        });
    }

    private void connectSoopList() {
        for (Map<String, String> soopUser : soopUserList) {
            try {
                connectSoop(soopUser);
            } catch (Exception e) {
                Logger.error("[Soop][" + soopUser.get("nickname") + "] 연결 중 오류 발생: " + e.getMessage());
            }
        }
    }

    private void disconnectSoopList() {
        for (SoopWebSocket webSocket : soopWebSocketList) {
            webSocket.close();
        }

        soopWebSocketList.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("방송정보")) {
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "사용법: /방송정보 <플레이어 닉네임>");
                return true;
            }

            String targetPlayer = args[0];
            boolean found = false;

            Component message = Component.text("\n[ 방송 정보 ]\n", NamedTextColor.YELLOW);

            // 치지직 방송 정보 확인
            for (Map<String, String> chzzkUser : chzzkUserList) {
                if (chzzkUser.get("tag").equalsIgnoreCase(targetPlayer)) {
                    String chzzkId = chzzkUser.get("id");
                    String chzzkUrl = "https://chzzk.naver.com/" + chzzkId;

                    Component chzzkLink = Component.text("[치지직 방송 바로가기]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.openUrl(chzzkUrl))
                            .hoverEvent(HoverEvent.showText(Component.text("치지직 방송 페이지로 이동")));

                    message = message.append(Component.text("\n- ", NamedTextColor.GRAY)).append(chzzkLink);
                    found = true;
                }
            }

            // 숲 방송 정보 확인
            for (Map<String, String> soopUser : soopUserList) {
                if (soopUser.get("tag").equalsIgnoreCase(targetPlayer)) {
                    String soopId = soopUser.get("id");
                    String soopUrl = "https://www.sooplive.co.kr/" + soopId;

                    Component soopLink = Component.text("[숲 방송 바로가기]")
                            .color(NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(soopUrl))
                            .hoverEvent(HoverEvent.showText(Component.text("숲 방송 페이지로 이동")));

                    message = message.append(Component.text("\n- ", NamedTextColor.GRAY)).append(soopLink);
                    found = true;
                }
            }

            // 방송 정보가 없을 경우 ------------------------------------------------
            if (!found) {
                sender.sendMessage(ChatColor.RED + targetPlayer + "님의 방송 정보를 찾을 수 없습니다.");
                return true;
            }

            // JSON 메시지 출력
            sender.sendMessage(message);
            return true;
        }

        // `/api` 명령어 처리
        if (command.getName().equalsIgnoreCase("api")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "이 명령어는 플레이어만 사용할 수 없습니다.");
                return true;
            }

            Player player = (Player) sender;
            String playerName = player.getName();
            boolean found = false;
            StringBuilder statusMessage = new StringBuilder(ChatColor.YELLOW + "\n[ API 상태 ]\n" + ChatColor.GRAY + " \n\n현재 활성화 되어있는 플랫폼\n");

            for (Map<String, String> chzzkUser : chzzkUserList) {
                if (chzzkUser.get("tag").equalsIgnoreCase(playerName)) {
                    boolean isLive = liveStatus.getOrDefault(chzzkUser.get("id"), false);
                    statusMessage.append(ChatColor.WHITE + "\n[ 치지직 ]\n방송상태: ").append(isLive ? ChatColor.GREEN + "ON\n" : ChatColor.RED + "OFF\n");
                    found = true;
                }
            }

            for (Map<String, String> soopUser : soopUserList) {
                if (soopUser.get("tag").equalsIgnoreCase(playerName)) {
                    boolean isLive = liveStatus.getOrDefault(soopUser.get("id"), false);
                    statusMessage.append(ChatColor.WHITE + "\n[ 숲 ]\n방송상태: ").append(isLive ? ChatColor.GREEN + "ON\n" : ChatColor.RED + "OFF\n");
                    found = true;
                }
            }

            if (!found) {
                sender.sendMessage(ChatColor.RED + "현재 연결된 방송 플랫폼이 없습니다.");
            } else {
                sender.sendMessage(statusMessage.toString());
            }
            return true;
        }

        // `/done` 명령어 처리
        if (command.getName().equalsIgnoreCase("done")) {
            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "사용법: /done [on|off|reconnect|reload|add|sheetreload]");
                return true;
            }

            String cmd = args[0].toLowerCase();

            try {
                switch (cmd) {
                    case "on":
                        Logger.warn("후원 기능을 활성화 합니다.");
                        connectChzzkList();
                        connectSoopList();
                        sender.sendMessage(ChatColor.GREEN + "후원 기능이 활성화되었습니다.");
                        break;

                    case "off":
                        Logger.warn("후원 기능을 비활성화 합니다.");
                        disconnectChzzkList();
                        disconnectSoopList();
                        sender.sendMessage(ChatColor.RED + "후원 기능이 비활성화되었습니다.");
                        break;

                    case "reconnect":
                        if (args.length < 2) {
                            sender.sendMessage(ChatColor.RED + "사용법: /done reconnect <all|닉네임>");
                            return true;
                        }
                        String target = args[1];

                        if (target.equalsIgnoreCase("all")) {
                            disconnectChzzkList();
                            disconnectSoopList();
                            connectChzzkList();
                            connectSoopList();
                            sender.sendMessage(ChatColor.GREEN + "모든 후원 기능이 재접속되었습니다.");
                        } else {
                            disconnectByNickName(target);
                            sender.sendMessage(ChatColor.GREEN + "[" + target + "] 재접속 완료.");
                        }
                        break;

                    case "reload":
                        Logger.warn("후원 설정을 다시 불러옵니다.");

                        disconnectChzzkList();
                        disconnectSoopList();
                        clearConfig();
                        loadConfig();
                        loadUsersAndConnectAsync();
                        sender.sendMessage(ChatColor.GREEN + "[Done] 설정이 성공적으로 리로드되었습니다!");

                        if (WhiteList) {
                            refreshWhitelistAsync();
                        }

                        if (SheetMode) { // sheetMode가 true일 때만 실행
                            sheet = new Sheet(this);
                            Bukkit.getScheduler().runTaskAsynchronously(this, () -> sheet.fetchAndSaveData()); // 비동기 실행
                            sender.sendMessage(ChatColor.YELLOW + "[Sheet] Google Sheets 데이터를 다시 불러옵니다...");
                            sheet.reloadSheetConfig();
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, sheet::fetchAndSaveData);
                            sender.sendMessage(ChatColor.GREEN + "[Sheet] Google Sheets 데이터가 성공적으로 리로드되었습니다!");
                        }

                        break;

                    case "sheetreload":
                        if (SheetMode) {
                            Logger.warn("[Sheet] Google Sheets 데이터를 다시 불러옵니다.");
                            sheet.reloadSheetConfig();
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, sheet::fetchAndSaveData);
                            sender.sendMessage(ChatColor.GREEN + "[Sheet] Google Sheets 데이터가 성공적으로 리로드되었습니다!");
                        }
                        else{
                            sender.sendMessage(ChatColor.RED + "[Sheet] config.yml 에서 SheetMode 를 true 로 바꿔주세요!");
                        }

                        break;

                    default:
                        sender.sendMessage(ChatColor.RED + "알 수 없는 명령어입니다.");
                        return true;
                }
            } catch (Exception e) {
                Logger.error("커맨드 수행 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }

            return true;
        }

        sender.sendMessage(ChatColor.RED + "알 수 없는 명령어입니다.");
        return false;
    }


    public List<String> onTabComplete(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("done") == false) {
            return Collections.emptyList();
        }

        if (sender.isOp() == false) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> commandList = new ArrayList<>(Arrays.asList("on", "off", "reconnect", "reload", "add", "sheetreload"));

            if (args[0].isEmpty()) {
                return commandList;
            } else {
                return commandList.stream()
                        .filter((command) -> command.toLowerCase().startsWith(args[0].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reconnect")) {
            if (args[1].isEmpty()) {
                return new ArrayList<>(List.of("all"));
            } else {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        return Collections.emptyList();
    }

    private void checkAndConnectLiveStreams() {
        for (Map<String, String> chzzkUser : chzzkUserList) {
            String chzzkId = chzzkUser.get("id");
            String minecraftName = chzzkUser.get("tag");
            isChzzkLive(chzzkId).thenAccept(isLive -> {
                boolean wasLive = liveStatus.getOrDefault(chzzkId, false);

                if (isLive != wasLive) { // 상태 변경이 있을 경우에만 실행
                    if (isLive) {
                        Logger.info("[Chzzk][" + chzzkUser.get("nickname") + "] 방송이 시작되었습니다! 연결 시도...");
                        connectChzzk(chzzkUser);
                        if (markLive) {
                            Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송켜짐 " + minecraftName));
                        }
                    } else {
                        Logger.info("[Chzzk][" + chzzkUser.get("nickname") + "] 방송이 종료되었습니다! 연결 해제...");
                        disconnectByNickName(chzzkUser.get("nickname"));
                        if (markLive) {
                            Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송꺼짐 " + minecraftName));
                        }
                    }
                    liveStatus.put(chzzkId, isLive);
                }
            });
        }

        for (Map<String, String> soopUser : soopUserList) {
            String soopId = soopUser.get("id");
            String minecraftName = soopUser.get("tag");
            isSoopLive(soopId).thenAccept(isLive -> {
                boolean wasLive = liveStatus.getOrDefault(soopId, false);

                if (isLive != wasLive) { // 상태 변경이 있을 경우에만 실행
                    if (isLive) {
                        Logger.info("[Soop][" + soopUser.get("nickname") + "] 방송이 시작되었습니다! 연결 시도...");
                        connectSoop(soopUser);
                        if (markLive) {
                            Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송켜짐 " + minecraftName));
                        }
                    } else {
                        Logger.info("[Soop][" + soopUser.get("nickname") + "] 방송이 종료되었습니다! 연결 해제...");
                        disconnectByNickName(soopUser.get("nickname"));
                        if (markLive) {
                            Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송꺼짐 " + minecraftName));
                        }
                    }
                    liveStatus.put(soopId, isLive);
                }
            });
        }
    }

    

    private void startLiveStatusChecker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                // 온라인 플레이어가 없으면 API 호출을 하지 않음
                return;
            }
            try {
                checkAndConnectLiveStreams();
            } catch (Exception e) {
                Logger.error("방송 상태 확인 중 오류 발생: " + e.getMessage());
            }
        }, 0L, 20L * 5); // 5초마다 실행
    }

    private void loadUsersAndConnectAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                File userFile = new File(getDataFolder(), "user.yml");
                if (!userFile.exists()) saveResource("user.yml", false);
                FileConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);

                Logger.info("치지직 & 숲 아이디 로드 중...");
                chzzkUserList.clear();
                soopUserList.clear();
                loadUsers(userConfig, "치지직", chzzkUserList);
                loadUsers(userConfig, "숲", soopUserList);

                if (chzzkUserList.isEmpty() && soopUserList.isEmpty()) {
                    throw new DoneException(ExceptionCode.ID_NOT_FOUND);
                }

                // 웹소켓 연결
                connectChzzkList();
                connectSoopList();

                // 화이트리스트 새로고침 (비동기적으로 호출)
                if (WhiteList) {
                    refreshWhitelistAsync();
                }

                Logger.info(ChatColor.GREEN + "유저 정보 및 웹소켓 연결 완료.");

            } catch (Exception e) {
                Logger.error("유저 정보 로드 및 웹소켓 연결 중 오류 발생: " + e.getMessage());
                Logger.debug("오류 상세: " + e.getMessage());
            }
        });
    }

    public void refreshWhitelistAsync() {
        // config.yml 읽기
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("WhiteList", false)) {
            return; // 화이트리스트 기능 꺼져있으면 무시
        }

            // 1. 기존 화이트리스트 초기화
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (OfflinePlayer player : Bukkit.getWhitelistedPlayers()) {
                    player.setWhitelisted(false);
                }
            });

            // 2. user.yml 읽기
            File userFile = new File(plugin.getDataFolder(), "user.yml");
            if (!userFile.exists()) {
                plugin.getLogger().warning("user.yml 파일을 찾을 수 없습니다!");
                return;
            }
            FileConfiguration userConfig = YamlConfiguration.loadConfiguration(userFile);

            // 3. 데이터 탐색
            ConfigurationSection rootSection = userConfig.getConfigurationSection("");
            if (rootSection == null) {
                plugin.getLogger().warning("user.yml 안에 데이터가 없습니다!");
                return;
            }

            for (String key : rootSection.getKeys(false)) { // 예: "치지직"
                ConfigurationSection groupSection = rootSection.getConfigurationSection(key);
                if (groupSection == null) continue;

                for (String subKey : groupSection.getKeys(false)) { // 예: "스카치"
                    ConfigurationSection playerSection = groupSection.getConfigurationSection(subKey);
                    if (playerSection == null) continue;

                    String mcName = playerSection.getString("마크닉네임");
                    if (mcName != null && !mcName.isEmpty()) {
                        // 마인크래프트 닉네임 유효성 검사 (영문, 숫자, 밑줄만 허용)
                        if (!mcName.matches("^[a-zA-Z0-9_]{3,16}$")) {
                            plugin.getLogger().warning("유효하지 않은 마인크래프트 닉네임이 감지되었습니다: " + mcName + " (화이트리스트에서 제외됩니다)");
                            continue; // 유효하지 않은 닉네임은 건너뜁니다.
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(mcName);
                            offlinePlayer.setWhitelisted(true);
                            plugin.getLogger().info("화이트리스트 추가됨: " + mcName);
                        });
                    }
                }
            }
            Logger.info(ChatColor.GREEN + "화이트리스트 새로고침 완료.");
    }
}
