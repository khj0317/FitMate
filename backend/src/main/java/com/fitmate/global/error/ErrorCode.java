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
    UNSUPPORTED_IMAGE(HttpStatus.BAD_REQUEST, "JPG 또는 PNG 사진만 올릴 수 있어요."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "사진이 너무 커요. 10MB 이하로 올려 주세요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦아요. 1분 뒤에 다시 시도해 주세요."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않아요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않거나 만료되었습니다."),

    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정이에요."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 할 수 있어요."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없어요."),
    REPORT_ALREADY_HANDLED(HttpStatus.CONFLICT, "이미 처리된 신고예요."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증을 먼저 완료해 주세요."),
    EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "이메일은 비울 수 없습니다. 아이디·비밀번호 찾기에 사용돼요."),
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

    // 모임
    GATHERING_NOT_FOUND(HttpStatus.NOT_FOUND, "모임을 찾을 수 없어요."),
    GATHERING_FULL(HttpStatus.CONFLICT, "정원이 다 찼거나 모집이 끝난 모임이에요."),
    GATHERING_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 모임이에요."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 모임이에요."),
    NOT_JOINED(HttpStatus.BAD_REQUEST, "참여하지 않은 모임이에요."),
    HOST_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "모임장은 나갈 수 없어요. 모임을 취소해 주세요."),
    NOT_GATHERING_HOST(HttpStatus.FORBIDDEN, "모임장만 할 수 있어요."),
    INVALID_GATHERING_TIME(HttpStatus.BAD_REQUEST, "모임 시간은 지금부터 10분 뒤 ~ 60일 안으로 정해 주세요."),

    // 커뮤니티
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "글을 찾을 수 없어요."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없어요."),
    NOT_POST_AUTHOR(HttpStatus.FORBIDDEN, "작성자만 할 수 있어요."),
    TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "사진은 4장까지 올릴 수 있어요."),

    // 매너 평가
    REVIEW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "함께 운동한 사람만 평가할 수 있어요."),
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "이미 평가한 상대예요."),

    // 채팅
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    CHAT_UNAVAILABLE(HttpStatus.FORBIDDEN, "메시지를 보낼 수 없는 채팅방이에요."),

    // 차단 · 신고
    USER_UNAVAILABLE(HttpStatus.BAD_REQUEST, "요청을 보낼 수 없는 사용자예요."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "이미 신고한 사용자예요. 운영자가 검토하고 있어요.");

    private final HttpStatus status;
    private final String message;
}
