package com.ludo.game.controller;

import com.ludo.game.dto.RoomDTOs;
import com.ludo.game.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RoomDTOs.CreateRoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.createRoom(userDetails.getUsername(), request.getMaxPlayers()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RoomDTOs.JoinRoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.joinRoom(userDetails.getUsername(), request.getRoomCode()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable String roomId) {
        try {
            return ResponseEntity.ok(roomService.getRoom(roomId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{roomId}/start")
    public ResponseEntity<?> startGame(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId) {
        try {
            roomService.startGame(userDetails.getUsername(), roomId);
            return ResponseEntity.ok(Map.of("message", "Game started"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String roomId) {
        try {
            RoomDTOs.RoomResponse room = roomService.leaveRoom(userDetails.getUsername(), roomId);
            return ResponseEntity.ok(room != null ? room : Map.of("message", "Room deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
