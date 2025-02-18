package me.taromati.doneconnector;

import me.taromati.doneconnector.chzzk.ChzzkApi;
import me.taromati.doneconnector.chzzk.ChzzkWebSocket;
import me.taromati.doneconnector.exception.DoneException;
import me.taromati.doneconnector.exception.ExceptionCode;
import me.taromati.doneconnector.soop.SoopApi;
import me.taromati.doneconnector.soop.SoopLiveInfo;
import me.taromati.doneconnector.soop.SoopWebSocket;
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

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public final class DoneConnector extends JavaPlugin implements Listener {
    public static Plugin plugin;

    public static boolean debug;
    public static boolean random;
    public static boolean poong = false;

    private static final Map<String, Boolean> liveStatus = new HashMap<>();
    private static final List<Map<String, String>> chzzkUserList = new ArrayList<>();
    private static final List<Map<String, String>> soopUserList = new ArrayList<>();
    private static final HashMap<Integer, List<String>> donationRewards = new HashMap<>();
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
        connectChzzkList();
        connectSoopList();

        // Sheet 객체 생성 후 데이터 가져오기 (비동기 실행)
        sheet = new Sheet(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> sheet.fetchAndSaveData()); // 비동기 실행

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

    private void loadConfig() throws DoneException {
        // 기본 config.yml 로드
        this.saveDefaultConfig();
        FileConfiguration config = this.getConfig();

        // done.yml 추가 로드
        File doneFile = new File(getDataFolder(), "done.yml");
        if (!doneFile.exists()) {
            saveResource("done.yml", false);
        }
        FileConfiguration doneConfig = YamlConfiguration.loadConfiguration(doneFile);

        try {
            // done.yml 값 불러오기
            debug = doneConfig.getBoolean("디버그");
            random = doneConfig.getBoolean("랜덤 보상");
            if (doneConfig.contains("숲풍선갯수로출력")) {
                poong = doneConfig.getBoolean("숲풍선갯수로출력");
            }

            // 후원 보상 로드
            Logger.info("후원 보상 로드 중...");
            for (String price : Objects.requireNonNull(doneConfig.getConfigurationSection("후원 보상")).getKeys(false)) {
                donationRewards.put(Integer.valueOf(price), doneConfig.getStringList("후원 보상." + price));
            }

            if (donationRewards.isEmpty()) {
                throw new DoneException(ExceptionCode.REWARD_NOT_FOUND);
            }

            Logger.info(ChatColor.GREEN + "후원 보상 목록 " + donationRewards.size() + "개 로드 완료.");
        } catch (Exception e) {
            throw new DoneException(ExceptionCode.REWARD_PARSE_ERROR);
        }

        try {
            // config.yml에서 치지직/숲 정보 불러오기
            Logger.info("치지직 & 숲 아이디 로드 중...");
            loadUsers(config, "치지직", chzzkUserList);
            loadUsers(config, "숲", soopUserList);

            if (chzzkUserList.isEmpty() && soopUserList.isEmpty()) {
                throw new DoneException(ExceptionCode.ID_NOT_FOUND);
            }

        } catch (Exception e) {
            throw new DoneException(ExceptionCode.ID_NOT_FOUND);
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

    private boolean isChzzkLive(String chzzkId) {
        try {
            return ChzzkApi.isLive(chzzkId); // ChzzkApi에서 실제 방송 상태를 반환하도록 구현 필요
        } catch (Exception e) {
            Logger.error("치지직 방송 상태 확인 중 오류 발생: " + e.getMessage());
            return false;
        }
    }

    private boolean connectChzzk(Map<String, String> chzzkUser) {
        try {
            String chzzkId = chzzkUser.get("id");

            if (!isChzzkLive(chzzkId)) {
                Logger.info("[Chzzk][" + chzzkUser.get("nickname") + "] 현재 방송이 진행 중이지 않음.");
                return false;
            }

            String chatChannelId = ChzzkApi.getChatChannelId(chzzkId);
            String token = ChzzkApi.getAccessToken(chatChannelId);
            String accessToken = token.split(";")[0];
            String extraToken = token.split(";")[1];

            ChzzkWebSocket webSocket = new ChzzkWebSocket(
                    "wss://kr-ss1.chat.naver.com/chat", chatChannelId, accessToken, extraToken, chzzkUser, donationRewards);
            webSocket.connect();
            chzzkWebSocketList.add(webSocket);

            return true;
        } catch (Exception e) {
            Logger.error("[Chzzk][" + chzzkUser.get("nickname") + "] 방송 연결 중 오류 발생.");
            Logger.debug(e.getMessage());
            return false;
        }
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

    private boolean isSoopLive(String soopId) {
        try {
            return SoopApi.isLive(soopId); // SoopApi에서 실제 방송 상태를 반환하도록 구현 필요
        } catch (Exception e) {
            Logger.error("숲 방송 상태 확인 중 오류 발생: " + e.getMessage());
            return false;
        }
    }

    private boolean connectSoop(Map<String, String> soopUser) {
        String soopId = soopUser.get("id");

        if (!isSoopLive(soopId)) {
            Logger.info("[Soop][" + soopUser.get("nickname") + "] 현재 방송이 진행 중이지 않음.");
            return false;
        }

        try {
            SoopLiveInfo liveInfo = SoopApi.getPlayerLive(soopId);
            Draft_6455 draft6455 = new Draft_6455(
                    Collections.emptyList(),
                    Collections.singletonList(new Protocol("chat"))
            );
            SoopWebSocket webSocket = new SoopWebSocket(
                    "wss://" + liveInfo.CHDOMAIN() + ":" + liveInfo.CHPT() + "/Websocket/" + liveInfo.BJID(),
                    draft6455, liveInfo, soopUser, donationRewards, poong);
            webSocket.connect();
            soopWebSocketList.add(webSocket);

            return true;
        } catch (Exception e) {
            Logger.error("[Soop][" + soopUser.get("nickname") + "] 숲 채팅 연결 중 오류 발생.");
            Logger.debug(e.getMessage());
            return false;
        }
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
        //sender.sendMessage(ChatColor.YELLOW + "DEBUG: command.getName() = " + command.getName());

        if (command.getName().equalsIgnoreCase("api")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "이 명령어는 플레이어만 사용할 수 있습니다.");
                return true;
            }

            Player player = (Player) sender;
            String playerName = player.getName();
            boolean found = false;
            StringBuilder statusMessage = new StringBuilder(ChatColor.YELLOW + "\n[ API 상태 ]\n" + ChatColor.GRAY + " \n\n현제 활성화 되어있는 플렛폼\n");

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
            //sender.sendMessage(ChatColor.YELLOW + "DEBUG: '/done' 명령어 감지됨.");

            if (!sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(ChatColor.RED + "사용법: /done [on|off|reconnect|reload|add|ymlreload]");
                return true;
            }

            String cmd = args[0].toLowerCase();
            //sender.sendMessage(ChatColor.YELLOW + "DEBUG: 입력된 서브 명령어 = " + cmd);

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
                        sender.sendMessage(ChatColor.YELLOW + "DEBUG: 재접속 대상 = " + target);

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

                    case "add":
                        if (args.length < 5) {
                            sender.sendMessage(ChatColor.RED + "사용법: /done add <플랫폼> <방송닉> <방송ID> <마크닉>");
                            return true;
                        }
                        String platform = args[1];
                        String nickname = args[2];
                        String id = args[3];
                        String tag = args[4];

                        sender.sendMessage(ChatColor.YELLOW + "DEBUG: 플랫폼 = " + platform);
                        sender.sendMessage(ChatColor.YELLOW + "DEBUG: 방송닉 = " + nickname);
                        sender.sendMessage(ChatColor.YELLOW + "DEBUG: 방송ID = " + id);
                        sender.sendMessage(ChatColor.YELLOW + "DEBUG: 마크닉 = " + tag);

                        if (platform.equalsIgnoreCase("치지직")) {
                            connectChzzk(Map.of("nickname", nickname, "id", id, "tag", tag));
                            sender.sendMessage(ChatColor.GREEN + "치지직 플랫폼에 추가되었습니다.");
                        } else if (platform.equalsIgnoreCase("숲")) {
                            connectSoop(Map.of("nickname", nickname, "id", id, "tag", tag));
                            sender.sendMessage(ChatColor.GREEN + "숲 플랫폼에 추가되었습니다.");
                        } else {
                            sender.sendMessage(ChatColor.RED + "올바른 플랫폼을 입력하세요. (치지직 / 숲)");
                        }
                        break;

                    case "reload":
                        Logger.warn("후원 설정을 다시 불러옵니다.");

                        // Google Sheets 데이터 리로드
                        sheet.reloadSheetConfig();
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, sheet::fetchAndSaveData);
                        sender.sendMessage(ChatColor.GREEN + "[Sheet] Google Sheets 데이터가 성공적으로 리로드되었습니다!");

                        // 기존 연결 해제 후 설정 다시 로드
                        disconnectChzzkList();
                        disconnectSoopList();
                        clearConfig();
                        loadConfig();
                        connectChzzkList();
                        connectSoopList();

                        sender.sendMessage(ChatColor.GREEN + "[Done] 설정이 성공적으로 리로드되었습니다!");
                        break;

                    case "ymlreload":
                        Logger.warn("[Sheet] Google Sheets 데이터를 다시 불러옵니다.");

                        // Google Sheets 데이터만 다시 로드
                        sheet.reloadSheetConfig();
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, sheet::fetchAndSaveData);
                        sender.sendMessage(ChatColor.GREEN + "[Sheet] Google Sheets 데이터가 성공적으로 리로드되었습니다!");
                        break;

                    default:
                        sender.sendMessage(ChatColor.RED + "알 수 없는 명령어입니다.");
                        sender.sendMessage(ChatColor.YELLOW + "DEBUG: 잘못된 서브 명령어 입력됨 = " + cmd);
                        return true;
                }
            } catch (Exception e) {
                Logger.error("커맨드 수행 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
            }

            return true;
        }

        sender.sendMessage(ChatColor.RED + "DEBUG: 해당 명령어는 처리할 수 없습니다.");
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
            List<String> commandList = new ArrayList<>(Arrays.asList("on", "off", "reconnect", "reload", "add", "ymlreload"));

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
            boolean isLive = isChzzkLive(chzzkId);

            // 기존 방송 상태 확인 (없으면 기본값 false)
            boolean wasLive = liveStatus.getOrDefault(chzzkId, false);

            if (isLive && !wasLive) { // 방송이 시작됨
                Logger.info("[Chzzk][" + chzzkUser.get("nickname") + "] 방송이 시작되었습니다! 연결 시도...");
                connectChzzk(chzzkUser);
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송켜짐 " + minecraftName);
                });
            } else if (!isLive && wasLive) { // 방송이 종료됨
                Logger.info("[Chzzk][" + chzzkUser.get("nickname") + "] 방송이 종료되었습니다! 연결 해제...");
                disconnectByNickName(chzzkUser.get("nickname"));
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송꺼짐 " + minecraftName);
                });
            }

            // 현재 방송 상태 업데이트
            liveStatus.put(chzzkId, isLive);
        }

        for (Map<String, String> soopUser : soopUserList) {
            String soopId = soopUser.get("id");
            String minecraftName = soopUser.get("tag");
            boolean isLive = isSoopLive(soopId);
            boolean wasLive = liveStatus.getOrDefault(soopId, false);

            if (isLive && !wasLive) { // 방송이 시작됨
                Logger.info("[Soop][" + soopUser.get("nickname") + "] 방송이 시작되었습니다! 연결 시도...");
                try {
                    connectSoop(soopUser);
                } catch (Exception e) {
                    Logger.error("[Soop][" + soopUser.get("nickname") + "] 연결 중 오류 발생: " + e.getMessage());
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송켜짐 " + minecraftName);
                });
            } else if (!isLive && wasLive) { // 방송이 종료됨
                Logger.info("[Soop][" + soopUser.get("nickname") + "] 방송이 종료되었습니다! 연결 해제...");
                disconnectByNickName(soopUser.get("nickname"));
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "방송꺼짐 " + minecraftName);
                });
            }

            // 현재 방송 상태 업데이트
            liveStatus.put(soopId, isLive);
        }
    }

    private void startLiveStatusChecker() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                checkAndConnectLiveStreams();
            } catch (Exception e) {
                Logger.error("방송 상태 확인 중 오류 발생: " + e.getMessage());
            }
        }, 0L, 20L * 5); // 5초마다 실행
    }
}
