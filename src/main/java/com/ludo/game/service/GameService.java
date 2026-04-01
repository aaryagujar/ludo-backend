package com.ludo.game.service;

import com.ludo.game.dto.GameDTOs;
import com.ludo.game.model.GameState;
import com.ludo.game.model.Room;
import com.ludo.game.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single authoritative game service.
 * Backend is the source of truth for dice, turns, moves, captures, and win detection.
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private static final Logger logger = LoggerFactory.getLogger(GameService.class);
    private static final String[] COLORS = {"red", "blue", "green", "yellow"};

    // roomId -> GameState (in-memory, one per active game)
    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    private final RoomRepository roomRepository;

    // ── Game Lifecycle ─────────────────────────────────────────────

    public GameState initGame(String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        List<String> playerOrder = new ArrayList<>();
        Map<String, String> playerColors = new LinkedHashMap<>();
        Map<String, int[]> pawnPositions = new LinkedHashMap<>();
        Map<String, Set<Integer>> finishedPawns = new LinkedHashMap<>();

        for (int i = 0; i < room.getPlayers().size(); i++) {
            String username = room.getPlayers().get(i).getUsername();
            playerOrder.add(username);
            playerColors.put(username, COLORS[i]);
            pawnPositions.put(username, new int[]{
                GameState.BASE_POSITION, GameState.BASE_POSITION,
                GameState.BASE_POSITION, GameState.BASE_POSITION
            });
            finishedPawns.put(username, new HashSet<>());
        }

        GameState state = GameState.builder()
                .roomId(roomId)
                .playerOrder(playerOrder)
                .playerColors(playerColors)
                .pawnPositions(pawnPositions)
                .finishedPawns(finishedPawns)
                .currentPlayerIndex(0)
                .lastDiceValue(null)
                .diceRolled(false)
                .consecutiveSixes(0)
                .winner(null)
                .gameOver(false)
                .disconnectedPlayers(new HashSet<>())
                .build();

        activeGames.put(roomId, state);
        logger.info("Game initialized for room {}, players: {}", roomId, playerOrder);
        return state;
    }

    public GameState getGameState(String roomId) {
        return activeGames.get(roomId);
    }

    public void removeGame(String roomId) {
        activeGames.remove(roomId);
        logger.info("Game removed for room {}", roomId);
    }

    // ── Dice Rolling ───────────────────────────────────────────────

    public GameDTOs.DiceRolledPayload rollDice(String roomId, String username) {
        GameState state = getValidatedState(roomId);

        if (!state.getCurrentPlayer().equals(username)) {
            throw new IllegalStateException("It is not your turn");
        }
        if (state.isDiceRolled()) {
            throw new IllegalStateException("You have already rolled the dice this turn");
        }

        int dice = new Random().nextInt(6) + 1;
        state.setLastDiceValue(dice);
        state.setDiceRolled(true);

        if (dice == 6) {
            state.setConsecutiveSixes(state.getConsecutiveSixes() + 1);
        } else {
            state.setConsecutiveSixes(0);
        }

        // 3 consecutive sixes — forfeit turn
        if (state.getConsecutiveSixes() >= 3) {
            logger.info("{} rolled 3 consecutive sixes — turn forfeited", username);
            state.advanceTurn();
            return GameDTOs.DiceRolledPayload.builder()
                    .rolledBy(username)
                    .diceValue(dice)
                    .canMove(false)
                    .movablePawnIndices(Collections.emptyList())
                    .build();
        }

        List<Integer> movable = getMovablePawns(state, username, dice);
        boolean canMove = !movable.isEmpty();

        // No moves available and not a 6 — auto advance turn
        if (!canMove && dice != 6) {
            state.advanceTurn();
        }

        logger.debug("{} rolled {} in room {}, movable: {}", username, dice, roomId, movable);

        return GameDTOs.DiceRolledPayload.builder()
                .rolledBy(username)
                .diceValue(dice)
                .canMove(canMove)
                .movablePawnIndices(movable)
                .build();
    }

    // ── Pawn Movement ──────────────────────────────────────────────

    public GameDTOs.PawnMovedPayload movePawn(String roomId, String username, int pawnIndex) {
        GameState state = getValidatedState(roomId);

        if (!state.getCurrentPlayer().equals(username)) {
            throw new IllegalStateException("It is not your turn");
        }
        if (!state.isDiceRolled()) {
            throw new IllegalStateException("You must roll the dice first");
        }
        if (pawnIndex < 0 || pawnIndex > 3) {
            throw new IllegalArgumentException("Invalid pawn index: " + pawnIndex);
        }

        int dice = state.getLastDiceValue();
        int[] positions = state.getPawnPositions().get(username);
        List<Integer> movable = getMovablePawns(state, username, dice);

        if (!movable.contains(pawnIndex)) {
            throw new IllegalStateException("Pawn " + pawnIndex + " cannot move with dice value " + dice);
        }

        int fromPos = positions[pawnIndex];
        int newPos;

        if (fromPos == GameState.BASE_POSITION) {
            // Enter board — requires 6
            newPos = GameState.ENTRY_POSITION;
        } else {
            newPos = fromPos + dice;
            if (newPos > GameState.HOME_POSITION) {
                throw new IllegalStateException("Move would overshoot home");
            }
        }

        positions[pawnIndex] = newPos;

        // Mark pawn as finished if it reached home
        if (newPos == GameState.HOME_POSITION) {
            state.getFinishedPawns().get(username).add(pawnIndex);
            logger.info("{}'s pawn {} reached home in room {}", username, pawnIndex, roomId);
        }

        // Check capture (only on board squares, not safe squares)
        String capturedPlayer = null;
        int capturedPawnIndex = -1;

        if (newPos != GameState.HOME_POSITION && !GameState.SAFE_SQUARES.contains(newPos)) {
            CaptureResult capture = checkCapture(state, username, newPos);
            if (capture != null) {
                capturedPlayer = capture.player;
                capturedPawnIndex = capture.pawnIndex;
                state.getPawnPositions().get(capturedPlayer)[capturedPawnIndex] = GameState.BASE_POSITION;
                logger.info("{} captured {}'s pawn {} at position {}", username, capturedPlayer, capturedPawnIndex, newPos);
            }
        }

        // Check win condition
        if (state.allPawnsHome(username)) {
            state.setWinner(username);
            state.setGameOver(true);
            logger.info("{} wins the game in room {}!", username, roomId);
        }

        boolean captured = capturedPlayer != null;

        // Turn management
        if (!state.isGameOver()) {
            if (dice == 6 || captured) {
                // Extra turn — just reset dice
                state.setDiceRolled(false);
                state.setLastDiceValue(null);
                if (dice != 6) state.setConsecutiveSixes(0);
            } else {
                state.advanceTurn();
            }
        }

        return GameDTOs.PawnMovedPayload.builder()
                .player(username)
                .pawnIndex(pawnIndex)
                .fromPosition(fromPos)
                .toPosition(newPos)
                .captured(captured)
                .capturedPlayer(capturedPlayer)
                .capturedPawnIndex(capturedPawnIndex)
                .pawnPositions(deepCopyPositions(state.getPawnPositions()))
                .build();
    }

    // ── Disconnect handling ────────────────────────────────────────

    public void handlePlayerDisconnect(String roomId, String username) {
        GameState state = activeGames.get(roomId);
        if (state == null) return;
        state.getDisconnectedPlayers().add(username);
        if (state.getCurrentPlayer().equals(username)) {
            state.advanceTurn();
            logger.info("Skipped turn for disconnected player {} in room {}", username, roomId);
        }
    }

    // ── Private helpers ────────────────────────────────────────────

    private List<Integer> getMovablePawns(GameState state, String username, int dice) {
        int[] positions = state.getPawnPositions().get(username);
        List<Integer> movable = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            if (state.getFinishedPawns().get(username).contains(i)) continue;
            int pos = positions[i];
            if (pos == GameState.BASE_POSITION) {
                if (dice == 6) movable.add(i);
            } else {
                if (pos + dice <= GameState.HOME_POSITION) movable.add(i);
            }
        }
        return movable;
    }

    private CaptureResult checkCapture(GameState state, String movingPlayer, int landingPos) {
        for (Map.Entry<String, int[]> entry : state.getPawnPositions().entrySet()) {
            String player = entry.getKey();
            if (player.equals(movingPlayer)) continue;
            int[] positions = entry.getValue();
            for (int i = 0; i < 4; i++) {
                if (state.getFinishedPawns().get(player).contains(i)) continue;
                if (positions[i] == landingPos) return new CaptureResult(player, i);
            }
        }
        return null;
    }

    private GameState getValidatedState(String roomId) {
        GameState state = activeGames.get(roomId);
        if (state == null) throw new IllegalStateException("No active game for room: " + roomId);
        if (state.isGameOver()) throw new IllegalStateException("Game is already over");
        return state;
    }

    private Map<String, int[]> deepCopyPositions(Map<String, int[]> original) {
        Map<String, int[]> copy = new LinkedHashMap<>();
        original.forEach((k, v) -> copy.put(k, Arrays.copyOf(v, v.length)));
        return copy;
    }

    private static class CaptureResult {
        final String player;
        final int pawnIndex;
        CaptureResult(String player, int pawnIndex) {
            this.player = player;
            this.pawnIndex = pawnIndex;
        }
    }
}
