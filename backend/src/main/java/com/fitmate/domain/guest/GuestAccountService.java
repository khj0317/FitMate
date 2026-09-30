package com.fitmate.domain.guest;

import com.fitmate.domain.chat.ChatMessage;
import com.fitmate.domain.chat.ChatMessageRepository;
import com.fitmate.domain.chat.ChatRoom;
import com.fitmate.domain.chat.ChatRoomRepository;
import com.fitmate.domain.matchrequest.MatchRequest;
import com.fitmate.domain.matchrequest.MatchRequestRepository;
import com.fitmate.domain.sport.Sport;
import com.fitmate.domain.sport.SportRepository;
import com.fitmate.domain.user.Gender;
import com.fitmate.domain.user.SkillLevel;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserAvailableTime;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.util.GeoPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 로그인 화면의 "체험하기": 가입 없이 바로 둘러볼 수 있는 1회용 계정을 만든다.
 * - 방문자마다 따로 만들어서 서로의 체험이 섞이지 않고, 공용 계정처럼 누가 비밀번호를 바꿔 버릴 일도 없다
 * - 데모 사용자들이 모여 있는 성수역에 프로필을 채워 두고, 데모 사용자와의 채팅·받은 매칭 요청을 하나씩 만들어 둔다
 * - 비밀번호는 아무도 모르는 난수라 이 요청으로 받은 토큰으로만 쓸 수 있고, 24시간 뒤 GuestCleanupScheduler가 지운다
 */
@Service
@RequiredArgsConstructor
public class GuestAccountService {

    public static final Duration LIFETIME = Duration.ofHours(24);
    /** 동시에 있을 수 있는 체험 계정 수 (IP를 바꿔 가며 계정을 찍어내도 DB가 끝없이 불어나지 않게) */
    static final int MAX_ACTIVE = 200;
    private static final double LATITUDE = 37.5446;   // 성수역
    private static final double LONGITUDE = 127.0559;
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final UserRepository userRepository;
    private final SportRepository sportRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final SecureRandom random = new SecureRandom();

    /** 체험 계정을 만들고 id를 돌려준다 */
    @Transactional
    public Long create() {
        if (userRepository.countByRole(User.Role.GUEST) >= MAX_ACTIVE) {
            throw new BusinessException(ErrorCode.GUEST_UNAVAILABLE);
        }
        String loginId = "guest_" + randomText(10);
        User guest = User.guest(loginId, passwordEncoder.encode(randomText(32)), nickname());
        guest.changeEmail(loginId + "@guest.fitmate.invalid"); // .invalid는 실제로 존재할 수 없는 도메인
        guest.changeGender(random.nextBoolean() ? Gender.MALE : Gender.FEMALE);
        guest.changeBirthDate(LocalDate.of(1998, 5, 20));
        guest.changeBio("FitMate를 체험 중이에요 👋");
        guest.changeActivityLocation(GeoPoints.of(LATITUDE, LONGITUDE), "서울 성동구 성수동");
        guest.changeSearchRadiusKm((short) 10);
        List<Sport> sports = sportRepository.findAll(Sort.by("id")); // 헬스, 러닝 순
        guest.replaceSports(Map.of(sports.get(0), SkillLevel.INTERMEDIATE, sports.get(1), SkillLevel.BEGINNER));
        guest.replaceAvailableTimes(List.of(
                new UserAvailableTime.Slot(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(21, 0)),
                new UserAvailableTime.Slot(DayOfWeek.WEDNESDAY, LocalTime.of(19, 0), LocalTime.of(21, 0)),
                new UserAvailableTime.Slot(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        userRepository.save(guest);

        // 데모 사용자가 있으면(배포·로컬) 채팅과 받은 요청을 미리 만들어 둔다
        Sport gym = sports.get(0);
        userRepository.findByLoginId("demo02").ifPresent(mate -> {
            MatchRequest accepted = new MatchRequest(mate, guest, gym, "성수역 근처에서 같이 운동해요!");
            ChatRoom room = chatRoomRepository.save(ChatRoom.direct(guest, mate));
            accepted.accept(room);
            matchRequestRepository.save(accepted);
            chatMessageRepository.save(new ChatMessage(room.getId(), mate.getId(), "안녕하세요! 요청 수락해 주셔서 감사해요"));
            chatMessageRepository.save(new ChatMessage(room.getId(), mate.getId(), "이번 주 수요일 저녁 7시에 성수역에서 같이 운동할래요?"));
        });
        userRepository.findByLoginId("demo03").ifPresent(requester ->
                matchRequestRepository.save(new MatchRequest(requester, guest, gym, "주말 아침 헬스 같이 하실래요?")));
        return guest.getId();
    }

    /** 체험러1234 형태. 겹치면 다시 뽑는다 (그래도 동시에 겹치면 DB 유니크 제약이 막는다) */
    private String nickname() {
        String nickname;
        do {
            nickname = "체험러%04d".formatted(random.nextInt(10_000));
        } while (userRepository.existsByNickname(nickname));
        return nickname;
    }

    private String randomText(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }
}
