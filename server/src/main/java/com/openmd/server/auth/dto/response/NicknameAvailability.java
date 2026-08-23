package com.openmd.server.auth.dto.response;

public record NicknameAvailability(boolean available, String checkedNickname) {
}
