package com.ludo.game.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

public class GameDTOs {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GameEvent {
        private String type;
        private Object payload;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GameStartedPayload {
        private List<RoomDTOs.PlayerInfo> players;
        private String currentPlayer;
        private Map<String, int[]> pawnPositions;
        private Map<String, String> playerColors;
        private String visualMode;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DiceRolledPayload {
        private String rolledBy;
        private int diceValue;
        private boolean canMove;
        private List<Integer> movablePawnIndices;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PawnMovedPayload {
        private String player;
        private int pawnIndex;
        private int fromPosition;
        private int toPosition;
        private boolean captured;
        private String capturedPlayer;
        private int capturedPawnIndex;
        private Map<String, int[]> pawnPositions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class KillEventPayload {
        private String attackerUsername;
        private String attackerColor;
        private int    attackerPawnIndex;
        private String attackerCharName;
        private String attackerCharTitle;
        private String attackerEmoji;
        private String attackType;
        private String attackIcon;
        private String defenderUsername;
        private String defenderColor;
        private int    defenderPawnIndex;
        private String defenderCharName;
        private String defenderCharTitle;
        private String defenderEmoji;
        private Map<String, int[]> pawnPositions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TurnChangedPayload {
        private String currentPlayer;
        private int diceValue;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class GameEndedPayload {
        private String winner;
        private String winnerDisplayName;
        private Map<String, int[]> finalPawnPositions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PlayerJoinedPayload {
        private RoomDTOs.PlayerInfo player;
        private int currentPlayerCount;
        private int maxPlayers;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PlayerLeftPayload {
        private String username;
        private int currentPlayerCount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MovePawnRequest {
        private String roomId;
        private int pawnIndex;
    }
}
