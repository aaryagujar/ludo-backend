package com.ludo.game.websocket;

import com.ludo.game.dto.GameDTOs;
import com.ludo.game.dto.RoomDTOs;
import com.ludo.game.model.GameState;
import com.ludo.game.model.Room;
import com.ludo.game.repository.RoomRepository;
import com.ludo.game.service.GameService;
import com.ludo.game.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class GameWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final GameService gameService;
    private final RoomService roomService;
    private final RoomRepository roomRepository;

    // sessionId -> { roomId, username }
    private final Map<String, Map<String, String>> sessionRoomMap = new ConcurrentHashMap<>();

    // ── Client → Server ───────────────────────────────────────────

    /** Player announces they joined the room lobby. /app/room/{roomId}/join */
    @MessageMapping("/room/{roomId}/join")
    public void handlePlayerJoin(@DestinationVariable String roomId,
                                  Principal principal,
                                  StompHeaderAccessor headerAccessor) {
        String username = principal.getName();
        String sessionId = headerAccessor.getSessionId();

        sessionRoomMap.put(sessionId, Map.of("roomId", roomId, "username", username));

        RoomDTOs.RoomResponse room = roomService.getRoom(roomId);
        RoomDTOs.PlayerInfo joiningPlayer = room.getPlayers().stream()
                .filter(p -> p.getUsername().equals(username))
                .findFirst()
                .orElse(new RoomDTOs.PlayerInfo(username, username, "unknown"));

        broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                .type("PLAYER_JOINED")
                .payload(GameDTOs.PlayerJoinedPayload.builder()
                        .player(joiningPlayer)
                        .currentPlayerCount(room.getPlayers().size())
                        .maxPlayers(room.getMaxPlayers())
                        .build())
                .build());
    }

    /** Host starts the game. /app/room/{roomId}/start */
    @MessageMapping("/room/{roomId}/start")
    public void handleStartGame(@DestinationVariable String roomId, Principal principal) {
        String username = principal.getName();
        try {
            roomService.startGame(username, roomId);
            GameState state = gameService.initGame(roomId);
            RoomDTOs.RoomResponse room = roomService.getRoom(roomId);
            List<RoomDTOs.PlayerInfo> players = room.getPlayers();

            broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                    .type("GAME_STARTED")
                    .payload(GameDTOs.GameStartedPayload.builder()
                            .players(players)
                            .currentPlayer(state.getCurrentPlayer())
                            .pawnPositions(state.getPawnPositions())
                            .playerColors(state.getPlayerColors())
                            .build())
                    .build());

            broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                    .type("TURN_CHANGED")
                    .payload(GameDTOs.TurnChangedPayload.builder()
                            .currentPlayer(state.getCurrentPlayer())
                            .diceValue(0)
                            .build())
                    .build());

        } catch (Exception e) {
            logger.error("Start game error in room {}: {}", roomId, e.getMessage());
            sendError(username, e.getMessage());
        }
    }

    /** Player rolls dice. /app/room/{roomId}/roll */
    @MessageMapping("/room/{roomId}/roll")
    public void handleRollDice(@DestinationVariable String roomId, Principal principal) {
        String username = principal.getName();
        try {
            GameDTOs.DiceRolledPayload payload = gameService.rollDice(roomId, username);

            broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                    .type("DICE_ROLLED")
                    .payload(payload)
                    .build());

            if (!payload.isCanMove()) {
                GameState state = gameService.getGameState(roomId);
                broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                        .type("TURN_CHANGED")
                        .payload(GameDTOs.TurnChangedPayload.builder()
                                .currentPlayer(state.getCurrentPlayer())
                                .diceValue(payload.getDiceValue())
                                .build())
                        .build());
            }

        } catch (Exception e) {
            logger.error("Roll dice error for {} in room {}: {}", username, roomId, e.getMessage());
            sendError(username, e.getMessage());
        }
    }

    /** Player moves a pawn. /app/room/{roomId}/move */
    @MessageMapping("/room/{roomId}/move")
    public void handleMovePawn(@DestinationVariable String roomId,
                                @Payload GameDTOs.MovePawnRequest request,
                                Principal principal) {
        String username = principal.getName();
        try {
            GameDTOs.PawnMovedPayload movePayload = gameService.movePawn(roomId, username, request.getPawnIndex());

            broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                    .type("PAWN_MOVED")
                    .payload(movePayload)
                    .build());

            // Broadcast KILL_EVENT so clients play the combat animation
            if (movePayload.isCaptured() && movePayload.getCapturedPlayer() != null) {
                broadcastKillEvent(roomId, username, movePayload);
            }

            GameState state = gameService.getGameState(roomId);

            if (state.isGameOver()) {
                Room room = roomRepository.findByRoomId(roomId).orElseThrow();
                String winnerDisplay = room.getPlayers().stream()
                        .filter(p -> p.getUsername().equals(state.getWinner()))
                        .map(p -> p.getDisplayName())
                        .findFirst()
                        .orElse(state.getWinner());

                roomService.finishGame(roomId);
                gameService.removeGame(roomId);

                broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                        .type("GAME_ENDED")
                        .payload(GameDTOs.GameEndedPayload.builder()
                                .winner(state.getWinner())
                                .winnerDisplayName(winnerDisplay)
                                .finalPawnPositions(movePayload.getPawnPositions())
                                .build())
                        .build());
            } else {
                broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                        .type("TURN_CHANGED")
                        .payload(GameDTOs.TurnChangedPayload.builder()
                                .currentPlayer(state.getCurrentPlayer())
                                .diceValue(state.getLastDiceValue() != null ? state.getLastDiceValue() : 0)
                                .build())
                        .build());
            }

        } catch (Exception e) {
            logger.error("Move pawn error for {} in room {}: {}", username, roomId, e.getMessage());
            sendError(username, e.getMessage());
        }
    }

    /** Player leaves room. /app/room/{roomId}/leave */
    @MessageMapping("/room/{roomId}/leave")
    public void handleLeaveRoom(@DestinationVariable String roomId, Principal principal) {
        handleLeave(roomId, principal.getName());
    }

    // ── Disconnect event ───────────────────────────────────────────

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Map<String, String> sessionInfo = sessionRoomMap.remove(sessionId);
        if (sessionInfo != null) {
            handleLeave(sessionInfo.get("roomId"), sessionInfo.get("username"));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void handleLeave(String roomId, String username) {
        try {
            gameService.handlePlayerDisconnect(roomId, username);

            broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                    .type("PLAYER_LEFT")
                    .payload(GameDTOs.PlayerLeftPayload.builder()
                            .username(username)
                            .currentPlayerCount(-1)
                            .build())
                    .build());

            roomService.leaveRoom(username, roomId);

        } catch (Exception e) {
            logger.warn("Leave room error for {} in {}: {}", username, roomId, e.getMessage());
        }
    }


    /**
     * Broadcast a KILL_EVENT containing character metadata so the
     * frontend can play the correct anime combat animation.
     * Character arrays mirror the frontend data/characters.js.
     */
    private void broadcastKillEvent(String roomId, String attackerUsername,
                                     GameDTOs.PawnMovedPayload move) {
        // --- character metadata tables (index by color: red=0 blue=1 green=2 yellow=3) ---
        String[] colorOrder = {"red", "blue", "green", "yellow"};

        String[][] charNames = {
            {"Kaine",  "Seika",   "Ryota",   "Akira"},
            {"Ryuu",   "Mizuki",  "Hayate",  "Touka"},
            {"Ibuki",  "Kaito",   "Fuu",     "Shiki"},
            {"Raiden", "Suna",    "Solaris", "Raikoh"}
        };
        String[][] charTitles = {
            {"Flame Samurai", "Blood Assassin", "Inferno Mage",  "Crimson Knight"},
            {"Ice Sniper",    "Water Sorcerer", "Storm Ninja",   "Frost Guardian"},
            {"Nature Monk",   "Poison Rogue",   "Wind Blade",    "Forest Spirit"},
            {"Lightning Warrior", "Sand Controller", "Solar Mage", "Thunder Beast"}
        };
        String[][] charEmojis = {
            {"S",  "D",  "V",  "H"},
            {"T",  "W",  "C",  "I"},
            {"G",  "X",  "L",  "E"},
            {"Z",  "U",  "O",  "R"}
        };
        // Attack type strings understood by the frontend CSS
        String[][] attackTypes = {
            {"fire",      "slash",   "fire",      "slash"},
            {"ice",       "ice",     "wind",      "ice"},
            {"nature",    "poison",  "wind",      "nature"},
            {"lightning", "sand",    "lightning", "lightning"}
        };
        // Attack icon (plain ASCII-safe substitutes — frontend maps these)
        String[][] attackIcons = {
            {"FIRE", "SLASH", "FIRE",  "SLASH"},
            {"ICE",  "ICE",   "WIND",  "ICE"},
            {"LEAF", "POIS",  "WIND",  "LEAF"},
            {"BOLT", "SAND",  "BOLT",  "BOLT"}
        };

        GameState state = gameService.getGameState(roomId);
        if (state == null) return;

        String atkColor = state.getPlayerColors().get(attackerUsername);
        String defColor = state.getPlayerColors().get(move.getCapturedPlayer());
        if (atkColor == null || defColor == null) return;

        int atkColorIdx = java.util.Arrays.asList(colorOrder).indexOf(atkColor);
        int defColorIdx = java.util.Arrays.asList(colorOrder).indexOf(defColor);
        if (atkColorIdx < 0 || defColorIdx < 0) return;

        int atkPawn = Math.min(move.getPawnIndex(), 3);
        int defPawn = Math.min(move.getCapturedPawnIndex(), 3);

        broadcastToRoom(roomId, GameDTOs.GameEvent.builder()
                .type("KILL_EVENT")
                .payload(GameDTOs.KillEventPayload.builder()
                        .attackerUsername(attackerUsername)
                        .attackerColor(atkColor)
                        .attackerPawnIndex(atkPawn)
                        .attackerCharName(charNames[atkColorIdx][atkPawn])
                        .attackerCharTitle(charTitles[atkColorIdx][atkPawn])
                        .attackerEmoji(charEmojis[atkColorIdx][atkPawn])
                        .attackType(attackTypes[atkColorIdx][atkPawn])
                        .attackIcon(attackIcons[atkColorIdx][atkPawn])
                        .defenderUsername(move.getCapturedPlayer())
                        .defenderColor(defColor)
                        .defenderPawnIndex(defPawn)
                        .defenderCharName(charNames[defColorIdx][defPawn])
                        .defenderCharTitle(charTitles[defColorIdx][defPawn])
                        .defenderEmoji(charEmojis[defColorIdx][defPawn])
                        .pawnPositions(move.getPawnPositions())
                        .build())
                .build());
    }

    private void broadcastToRoom(String roomId, GameDTOs.GameEvent event) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, event);
    }

    private void sendError(String username, String message) {
        messagingTemplate.convertAndSendToUser(username, "/queue/errors",
                Map.of("error", message));
    }
}
