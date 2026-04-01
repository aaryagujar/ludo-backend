package com.ludo.game.model;

import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameState {

    private String roomId;
    private List<String> playerOrder;
    private Map<String, String> playerColors;
    // username -> int[4] positions: -1=base, 1-56=board, 57=home
    private Map<String, int[]> pawnPositions;
    private Map<String, Set<Integer>> finishedPawns;
    private int currentPlayerIndex;
    private Integer lastDiceValue;
    private boolean diceRolled;
    private int consecutiveSixes;
    private String winner;
    private boolean gameOver;
    private Set<String> disconnectedPlayers;

    public static final int BASE_POSITION  = -1;
    public static final int HOME_POSITION  = 57;
    public static final int ENTRY_POSITION = 1;
    public static final Set<Integer> SAFE_SQUARES = Set.of(1, 9, 14, 22, 27, 35, 40, 48);

    public String getCurrentPlayer() {
        if (playerOrder == null || playerOrder.isEmpty()) return null;
        return playerOrder.get(currentPlayerIndex % playerOrder.size());
    }

    public void advanceTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % playerOrder.size();
        diceRolled = false;
        lastDiceValue = null;
        consecutiveSixes = 0;
    }

    public boolean allPawnsHome(String username) {
        int[] positions = pawnPositions.get(username);
        if (positions == null) return false;
        for (int pos : positions) {
            if (pos != HOME_POSITION) return false;
        }
        return true;
    }
}
