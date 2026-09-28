package com.fitmate.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호 확인이 일치하지 않습니다."),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "생년월일이 올바르지 않습니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    OVERLAPPING_AVAILABLE_TIMES(HttpStatus.BAD_REQUEST, "같은 요일에 겹치는 시간대가 있습니다."),
    DUPLICATE_SPORT(HttpStatus.BAD_REQUEST, "같은 운동 종목을 중복으로 등록할 수 없습니다."),

    // 운동 종목
    SPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 운동 종목입니다."),

    // 매칭
    LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "활동 지역을 먼저 설정해 주세요."),
    SPORT_REQUIRED(HttpStatus.BAD_REQUEST, "운동 종목을 먼저 등록하거나 검색할 종목을 선택해 주세요."),

    // 매칭 요청
    MATCH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "매칭 요청을 찾을 수 없습니다."),
    CANNOT_REQUEST_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게는 매칭 요청을 보낼 수 없습니다."),
    RECEIVER_DOES_NOT_PLAY_SPORT(HttpStatus.BAD_REQUEST, "상대방이 등록하지 않은 운동 종목입니다."),
    DUPLICATE_MATCH_REQUEST(HttpStatus.CONFLICT, "이미 대기 중인 매칭 요청이 있습니다."),
    REVERSE_MATCH_REQUEST_EXISTS(HttpStatus.CONFLICT, "상대방이 먼저 요청을 보냈습니다. 받은 요청에서 수락해 주세요."),
    ALREADY_MATCHED(HttpStatus.CONFLICT, "이미 매칭되어 채팅방이 있는 상대입니다."),
    INVALID_MATCH_REQUEST_STATUS(HttpStatus.CONFLICT, "이미 처리된 매칭 요청입니다."),

    // 채팅
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
