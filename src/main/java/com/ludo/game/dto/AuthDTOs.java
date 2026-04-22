package com.ludo.game.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

public class AuthDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        @NotBlank @Size(min = 3, max = 20)
        private String username;

        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 6, max = 40)
        private String password;

        private String displayName;
    }

    @Data
    public static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String username;
        private String email;
        private String displayName;

        public AuthResponse(String token, String username, String email, String displayName) {
            this.token = token;
            this.username = username;
            this.email = email;
            this.displayName = displayName;
        }
    }
}
