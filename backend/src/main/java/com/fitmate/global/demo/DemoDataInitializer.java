package com.fitmate.global.demo;

import com.fitmate.domain.chat.ChatMessage;
import com.fitmate.domain.chat.ChatMessageRepository;
import com.fitmate.domain.chat.ChatRoom;
import com.fitmate.domain.chat.ChatRoomRepository;
import com.fitmate.domain.matchrequest.MatchRequest;
import com.fitmate.domain.matchrequest.MatchRequestRepository;
import com.fitmate.domain.matchrequest.MatchRequestStatus;
import com.fitmate.domain.sport.Sport;
import com.fitmate.domain.sport.SportRepository;
import com.fitmate.domain.user.Gender;
import com.fitmate.domain.user.SkillLevel;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserAvailableTime;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.util.GeoPoints;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 로컬(local 프로필)에서만 성수역 주변에 데모 사용자를 만든다. 매칭 기능을 직접 눌러보기 위한 용도.
 * 계정: 아이디 demo01 ~ demo30 / 비밀번호 password123
 * demo01은 성수역 한가운데에 있고 헬스·러닝을 하므로 이 계정으로 로그인해서 추천을 확인하면 된다.
 * 채팅 확인용으로 demo01-demo02는 매칭 완료(대화 있음), demo03 → demo01은 대기 중 요청을 만든다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    static final String DEMO_PASSWORD = "password123";
    private static final int DEMO_USER_COUNT = 30;
    private static final double CENTER_LAT = 37.5446;   // 성수역
    private static final double CENTER_LNG = 127.0559;
    private static final double MAX_DISTANCE_KM = 8.0;
    private static final String[] AREAS = {"성수동", "건대입구", "왕십리", "뚝섬", "서울숲", "구의동", "자양동", "금호동"};

    private final UserRepository userRepository;
    private final SportRepository sportRepository;
    private final PasswordEncoder passwordEncoder;
    private final MatchRequestRepository matchRequestRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    /** 각 단계는 이미 데이터가 있으면 건너뛰므로 여러 번 실행해도 안전하다. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByLoginId(loginId(1))) {
            seedUsers();
        }
        seedMatches();
    }

    private void seedUsers() {
        Random random = new Random(42); // 매번 같은 데이터가 생성되도록 시드 고정
        List<Sport> sports = sportRepository.findAll(Sort.by("id"));
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD); // 해시는 느리므로 한 번만 계산

        for (int i = 1; i <= DEMO_USER_COUNT; i++) {
            User user = new User(loginId(i), passwordHash, "데모_%02d".formatted(i));
            user.changeEmail(loginId(i) + "@fitmate.com");
            if (i == 1) {
                user.changeGender(Gender.MALE);
                user.changeBirthDate(LocalDate.of(1997, 3, 17));
                user.changeActivityLocation(GeoPoints.of(CENTER_LAT, CENTER_LNG), "서울 성동구 성수동");
                user.replaceSports(Map.of(sports.get(0), SkillLevel.INTERMEDIATE, sports.get(1), SkillLevel.BEGINNER));
                user.replaceAvailableTimes(List.of(
                        new UserAvailableTime.Slot(DayOfWeek.MONDAY, LocalTime.of(19, 0), LocalTime.of(21, 0)),
                        new UserAvailableTime.Slot(DayOfWeek.WEDNESDAY, LocalTime.of(19, 0), LocalTime.of(21, 0)),
                        new UserAvailableTime.Slot(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));
                user.changeSearchRadiusKm((short) 10);
            } else {
                randomize(user, random, sports);
            }
            userRepository.save(user);
        }
        log.info("로컬 데모 사용자 {}명을 생성했습니다. (아이디 demo01 ~ demo{})",
                DEMO_USER_COUNT, DEMO_USER_COUNT);
    }

    private void seedMatches() {
        User demo1 = userRepository.findByLoginId(loginId(1)).orElseThrow();
        User demo2 = userRepository.findByLoginId(loginId(2)).orElseThrow();
        User demo3 = userRepository.findByLoginId(loginId(3)).orElseThrow();
        Sport gym = sportRepository.findAll(Sort.by("id")).get(0);

        if (!chatRoomRepository.existsByDirectKey(ChatRoom.directKey(demo1.getId(), demo2.getId()))) {
            MatchRequest accepted = new MatchRequest(demo2, demo1, gym, "성수역 근처에서 같이 운동해요!");
            ChatRoom room = chatRoomRepository.save(ChatRoom.direct(demo1, demo2));
            accepted.accept(room);
            matchRequestRepository.save(accepted);
            chatMessageRepository.save(new ChatMessage(room.getId(), demo2.getId(), "안녕하세요! 요청 수락해 주셔서 감사해요"));
            chatMessageRepository.save(new ChatMessage(room.getId(), demo2.getId(), "이번 주 수요일 저녁 7시 어떠세요?"));
            log.info("데모 채팅방을 만들었습니다. (demo01 ↔ demo02)");
        }

        boolean demo3Handled = matchRequestRepository.existsByRequesterIdAndReceiverIdAndStatus(
                demo3.getId(), demo1.getId(), MatchRequestStatus.PENDING)
                || chatRoomRepository.existsByDirectKey(ChatRoom.directKey(demo1.getId(), demo3.getId()));
        if (!demo3Handled) {
            matchRequestRepository.save(new MatchRequest(demo3, demo1, gym, "주말 아침 헬스 같이 하실래요?"));
        }
    }

    private void randomize(User user, Random random, List<Sport> sports) {
        // 중심에서 임의 방향으로 0~8km (위도 1도 ≈ 111km, 경도는 위도에 따라 줄어듦)
        double distanceKm = random.nextDouble() * MAX_DISTANCE_KM;
        double angle = random.nextDouble() * 2 * Math.PI;
        double lat = CENTER_LAT + (distanceKm * Math.cos(angle)) / 111.0;
        double lng = CENTER_LNG + (distanceKm * Math.sin(angle)) / (111.0 * Math.cos(Math.toRadians(CENTER_LAT)));
        user.changeActivityLocation(GeoPoints.of(lat, lng), "서울 " + AREAS[random.nextInt(AREAS.length)]);

        // 인기 종목(헬스, 러닝, 클라이밍) 위주로 1~3개
        List<Sport> pool = new ArrayList<>(sports.subList(0, 3));
        pool.add(sports.get(3 + random.nextInt(sports.size() - 3)));
        Collections.shuffle(pool, random);
        Map<Sport, SkillLevel> levels = new LinkedHashMap<>();
        int sportCount = 1 + random.nextInt(3);
        for (int i = 0; i < sportCount; i++) {
            levels.put(pool.get(i), SkillLevel.values()[random.nextInt(SkillLevel.values().length)]);
        }
        user.replaceSports(levels);

        // 평일 저녁 또는 주말 오전 위주로 1~3개
        List<UserAvailableTime.Slot> slots = new ArrayList<>();
        List<DayOfWeek> days = new ArrayList<>(List.of(DayOfWeek.values()));
        Collections.shuffle(days, random);
        for (DayOfWeek day : days.subList(0, 1 + random.nextInt(3))) {
            boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            int start = weekend ? 8 + random.nextInt(4) : 18 + random.nextInt(3);
            slots.add(new UserAvailableTime.Slot(day, LocalTime.of(start, 0), LocalTime.of(start + 2, 0)));
        }
        user.replaceAvailableTimes(slots);

        user.changeGender(random.nextBoolean() ? Gender.MALE : Gender.FEMALE);
        user.changeBirthDate(LocalDate.of(1985 + random.nextInt(20), 1 + random.nextInt(12), 1 + random.nextInt(28)));
    }

    private static String loginId(int index) {
        return "demo%02d".formatted(index);
    }
}
