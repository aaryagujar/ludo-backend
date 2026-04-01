package com.ludo.game.service;

import com.ludo.game.dto.RoomDTOs;
import com.ludo.game.model.Room;
import com.ludo.game.model.User;
import com.ludo.game.repository.RoomRepository;
import com.ludo.game.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private static final String[] COLORS = {"red", "blue", "green", "yellow"};
    private static final SecureRandom random = new SecureRandom();

    @Transactional
    public RoomDTOs.RoomResponse createRoom(String username, int maxPlayers) {
        User user = getUser(username);
        String roomId = UUID.randomUUID().toString();
        String roomCode = generateUniqueRoomCode();

        Room room = Room.builder()
                .roomId(roomId)
                .roomCode(roomCode)
                .maxPlayers(maxPlayers)
                .host(user)
                .build();

        room.getPlayers().add(user);
        room = roomRepository.save(room);
        return toRoomResponse(room);
    }

    @Transactional
    public RoomDTOs.RoomResponse joinRoom(String username, String roomCode) {
        User user = getUser(username);
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room not found with code: " + roomCode));

        if (room.getStatus() != Room.RoomStatus.WAITING) {
            throw new IllegalStateException("Room is not accepting players");
        }
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            throw new IllegalStateException("Room is full");
        }
        boolean alreadyIn = room.getPlayers().stream()
                .anyMatch(p -> p.getUsername().equals(username));
        if (alreadyIn) return toRoomResponse(room);

        room.getPlayers().add(user);
        room = roomRepository.save(room);
        return toRoomResponse(room);
    }

    @Transactional
    public RoomDTOs.RoomResponse leaveRoom(String username, String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        room.getPlayers().removeIf(p -> p.getUsername().equals(username));

        if (room.getPlayers().isEmpty()) {
            roomRepository.delete(room);
            return null;
        }
        if (room.getHost().getUsername().equals(username)) {
            room.setHost(room.getPlayers().get(0));
        }

        room = roomRepository.save(room);
        return toRoomResponse(room);
    }

    @Transactional
    public void startGame(String username, String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (!room.getHost().getUsername().equals(username)) {
            throw new IllegalStateException("Only the host can start the game");
        }
        if (room.getPlayers().size() < 2) {
            throw new IllegalStateException("Need at least 2 players to start");
        }
        if (room.getStatus() != Room.RoomStatus.WAITING) {
            throw new IllegalStateException("Game already started");
        }

        room.setStatus(Room.RoomStatus.STARTED);
        roomRepository.save(room);
    }

    @Transactional
    public void finishGame(String roomId) {
        roomRepository.findByRoomId(roomId).ifPresent(room -> {
            room.setStatus(Room.RoomStatus.FINISHED);
            roomRepository.save(room);
        });
    }

    public RoomDTOs.RoomResponse getRoom(String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        return toRoomResponse(room);
    }

    public RoomDTOs.RoomResponse toRoomResponse(Room room) {
        List<RoomDTOs.PlayerInfo> playerInfos = room.getPlayers().stream()
                .map(p -> new RoomDTOs.PlayerInfo(
                        p.getUsername(),
                        p.getDisplayName(),
                        getColorForPlayer(room, p.getUsername())
                ))
                .collect(Collectors.toList());

        return new RoomDTOs.RoomResponse(
                room.getRoomId(),
                room.getRoomCode(),
                room.getMaxPlayers(),
                room.getHost().getUsername(),
                playerInfos,
                room.getStatus()
        );
    }

    public String getColorForPlayer(Room room, String username) {
        List<User> players = room.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getUsername().equals(username)) {
                return i < COLORS.length ? COLORS[i] : "unknown";
            }
        }
        return "unknown";
    }

    private String generateUniqueRoomCode() {
        String code;
        do { code = generateRoomCode(); }
        while (roomRepository.existsByRoomCode(code));
        return code;
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
