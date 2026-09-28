package com.fitmate.global.demo;

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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 로컬(local 프로필)에서만 성수역 주변에 데모 사용자를 만든다. 매칭 기능을 직접 눌러보기 위한 용도.
 * 계정: demo01@fitmate.com ~ demo30@fitmate.com / 비밀번호 password123
 * demo01은 성수역 한가운데에 있고 헬스·러닝을 하므로 이 계정으로 로그인해서 추천을 확인하면 된다.
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

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(email(1))) {
            return;
        }

        Random random = new Random(42); // 매번 같은 데이터가 생성되도록 시드 고정
        List<Sport> sports = sportRepository.findAll(Sort.by("id"));
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD); // 해시는 느리므로 한 번만 계산

        for (int i = 1; i <= DEMO_USER_COUNT; i++) {
            User user = new User(email(i), passwordHash, "데모_%02d".formatted(i));
            if (i == 1) {
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
        log.info("로컬 데모 사용자 {}명을 생성했습니다. (demo01@fitmate.com ~ demo{}@fitmate.com)",
                DEMO_USER_COUNT, DEMO_USER_COUNT);
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
        user.changeBirthYear((short) (1985 + random.nextInt(20)));
    }

    private static String email(int index) {
        return "demo%02d@fitmate.com".formatted(index);
    }
}
