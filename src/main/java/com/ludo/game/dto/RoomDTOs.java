package com.ludo.game.dto;

import com.ludo.game.model.Room;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

public class RoomDTOs {

    @Data
    public static class CreateRoomRequest {
        @Min(2) @Max(4)
        private int maxPlayers;
    }

    @Data
    public static class JoinRoomRequest {
        @NotBlank
        private String roomCode;
    }

    @Data
    public static class PlayerInfo {
        private String username;
        private String displayName;
        private String color;

        public PlayerInfo(String username, String displayName, String color) {
            this.username = username;
            this.displayName = displayName;
            this.color = color;
        }
    }

    @Data
    public static class RoomResponse {
        private String roomId;
        private String roomCode;
        private int maxPlayers;
        private String hostUsername;
        private List<PlayerInfo> players;
        private Room.RoomStatus status;

        public RoomResponse(String roomId, String roomCode, int maxPlayers,
                            String hostUsername, List<PlayerInfo> players,
                            Room.RoomStatus status) {
            this.roomId = roomId;
            this.roomCode = roomCode;
            this.maxPlayers = maxPlayers;
            this.hostUsername = hostUsername;
            this.players = players;
            this.status = status;
        }
    }
}
