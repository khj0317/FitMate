package com.fitmate.global.demo;

import com.fitmate.domain.chat.ChatMessage;
import com.fitmate.domain.chat.ChatMessageRepository;
import com.fitmate.domain.chat.ChatRoom;
import com.fitmate.domain.chat.ChatRoomRepository;
import com.fitmate.domain.community.CommunityDtos;
import com.fitmate.domain.community.CommunityService;
import com.fitmate.domain.community.Post;
import com.fitmate.domain.community.PostRepository;
import com.fitmate.domain.gathering.GatheringRepository;
import com.fitmate.domain.gathering.GatheringService;
import com.fitmate.domain.gathering.dto.GatheringDtos;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 로컬 개발용으로 성수역 주변에 데모 사용자·모임·게시글을 만든다 (local 프로필에서 켜짐, 배포에서는 꺼짐).
 * 계정: 아이디 demo01 ~ demo30 / 비밀번호 password123
 * demo01은 성수역 한가운데에 있고 헬스·러닝을 하므로 이 계정으로 로그인해서 추천을 확인하면 된다.
 * 채팅 확인용으로 demo01-demo02는 매칭 완료(대화 있음), demo03 → demo01은 대기 중 요청을 만든다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fitmate.demo-data.enabled", havingValue = "true")
@Order(10) // 관리자 지정(AdminBootstrap)보다 먼저 데모 사용자를 만든다
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    static final String DEMO_PASSWORD = "password123";
    private static final int DEMO_USER_COUNT = 30;
    private static final double CENTER_LAT = 37.5446;   // 성수역
    private static final double CENTER_LNG = 127.0559;
    private static final double MAX_DISTANCE_KM = 8.0;
    private static final String[] AREAS = {"성수동", "건대입구", "왕십리", "뚝섬", "서울숲", "구의동", "자양동", "금호동"};
    /** demoNN의 닉네임: 성별에 맞춰 둘 중 하나 (V10 마이그레이션과 같은 값) */
    private static final String[] MALE_NAMES = {
            "김도현", "이준서", "박현우", "최민재", "정우진", "박준호", "이성민", "강지훈", "조현석", "윤재석",
            "정민혁", "최태윤", "강동욱", "한승우", "임현준", "송민규", "권태호", "유진우", "오태윤", "홍준영",
            "배성훈", "서동욱", "신유찬", "조영호", "문재민", "백승현", "노정훈", "전민규", "안재원", "장석진"};
    private static final String[] FEMALE_NAMES = {
            "김서윤", "이수아", "박지은", "최윤정", "김하린", "정다은", "이유나", "정미경", "강서연", "조은영",
            "한소영", "임채은", "송지아", "윤소희", "한지혜", "임다은", "권나연", "유가은", "송예린", "오세영",
            "문채원", "서지현", "신예진", "조수빈", "배수빈", "홍나영", "권민지", "유선영", "안시은", "장유진"};

    private final UserRepository userRepository;
    private final SportRepository sportRepository;
    private final PasswordEncoder passwordEncoder;
    private final MatchRequestRepository matchRequestRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GatheringRepository gatheringRepository;
    private final GatheringService gatheringService;
    private final PostRepository postRepository;
    private final CommunityService communityService;

    /** 각 단계는 이미 데이터가 있으면 건너뛰므로 여러 번 실행해도 안전하다. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        seedMatches();
        seedGatherings();
        seedPosts();
    }

    /** 빠진 데모 사용자만 만든다 (예: 누군가 데모 계정을 지웠어도 재시작하면 복구) */
    private void seedUsers() {
        if (countExisting() == DEMO_USER_COUNT) {
            return;
        }
        Random random = new Random(42); // 매번 같은 데이터가 생성되도록 시드 고정
        List<Sport> sports = sportRepository.findAll(Sort.by("id"));
        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD); // 해시는 느리므로 한 번만 계산
        int created = 0;

        for (int i = 1; i <= DEMO_USER_COUNT; i++) {
            User user = new User(loginId(i), passwordHash, "demo");
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
                randomize(user, random, sports); // 이미 있는 사용자도 호출해서 난수 순서를 항상 같게 유지한다
            }
            user.changeNickname((user.getGender() == Gender.MALE ? MALE_NAMES : FEMALE_NAMES)[i - 1]);
            if (!userRepository.existsByLoginId(loginId(i))) {
                userRepository.save(user);
                created++;
            }
        }
        log.info("데모 사용자 {}명을 생성했습니다. (아이디 demo01 ~ demo{})", created, DEMO_USER_COUNT);
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

    /**
     * 성수역 주변에 앞으로 열릴 모임 5개. demo01은 그중 하나에 참여해 있다.
     * 배포 환경에서는 시간이 지나면 모임이 모두 끝나 버리므로, 다가오는 데모 모임이 없을 때마다(재시작 시) 새로 만든다.
     */
    private void seedGatherings() {
        User host = user(4);
        if (gatheringRepository.existsByHostIdAndStartsAtAfter(host.getId(), Instant.now())) {
            return;
        }
        record Plan(int host, String sport, String title, String description, String place,
                    double dLat, double dLng, int daysLater, int hour, int capacity, int[] guests) {
        }
        List<Plan> plans = List.of(
                new Plan(4, "RUNNING", "서울숲 저녁 5km 러닝", "천천히 대화하면서 뛰는 페이스예요. 초보 환영!",
                        "서울숲 정문", 0.0, -0.011, 1, 19, 6, new int[]{1, 7, 9}),
                new Plan(5, "GYM", "주말 아침 하체 루틴 같이 해요", "스쿼트·런지 위주로 1시간 반. 자세 서로 봐줘요",
                        "성수역 3번 출구 앞", 0.001, 0.0, 3, 9, 3, new int[]{11}),
                new Plan(6, "BADMINTON", "뚝섬 배드민턴 복식", "라켓 여분 있어요. 끝나고 가볍게 커피",
                        "뚝섬유원지역 2번 출구", -0.012, 0.011, 4, 18, 4, new int[]{12, 13}),
                new Plan(8, "CLIMBING", "클라이밍 입문 번개", "처음이신 분도 괜찮아요. 암장 일일권 각자",
                        "건대입구역 2번 출구", 0.0, 0.014, 2, 20, 5, new int[]{}),
                new Plan(10, "HIKING", "아차산 해돋이 산행", "왕복 2시간 코스. 따뜻한 물 챙겨 오세요",
                        "아차산역 2번 출구", 0.006, 0.037, 6, 6, 8, new int[]{14, 15, 16, 17}));

        ZonedDateTime today = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).withMinute(0).withSecond(0).withNano(0);
        for (Plan plan : plans) {
            Short sportId = sportId(plan.sport());
            Instant startsAt = today.plusDays(plan.daysLater()).withHour(plan.hour()).toInstant();
            GatheringDtos.Detail created = gatheringService.create(user(plan.host()).getId(), new GatheringDtos.Create(
                    sportId, plan.title(), plan.description(), plan.place(),
                    new GatheringDtos.Point(CENTER_LAT + plan.dLat(), CENTER_LNG + plan.dLng()),
                    startsAt, (short) plan.capacity()));
            for (int guest : plan.guests()) {
                gatheringService.join(user(guest).getId(), created.summary().id());
            }
        }
        log.info("데모 모임 {}개를 만들었습니다.", plans.size());
    }

    /** 동네 게시판 글과 댓글. demo01의 글에도 댓글이 달려 있다 */
    private void seedPosts() {
        if (postRepository.existsByAuthorId(user(5).getId())) {
            return;
        }
        record Seed(int author, Post.Category category, String sport, String content, int[] commenters, String[] comments) {
        }
        List<Seed> seeds = List.of(
                new Seed(5, Post.Category.CERTIFY, "GYM", "오늘 데드리프트 100kg 드디어 성공했어요 🎉\n3개월 걸렸네요. 다들 화이팅!",
                        new int[]{1, 9}, new String[]{"와 축하해요! 자세 영상 있으면 보고 싶어요", "대단해요 👏"}),
                new Seed(7, Post.Category.QUESTION, "RUNNING", "러닝 입문했는데 무릎이 조금 아파요.\n러닝화 추천이나 스트레칭 팁 있을까요?",
                        new int[]{4, 12}, new String[]{"처음엔 거리보다 케이던스 올리는 게 좋아요. 서울숲 러닝 모임 와보세요!", "뛰기 전 종아리·햄스트링 스트레칭 꼭 하세요"}),
                new Seed(9, Post.Category.REVIEW, "CLIMBING", "성수동 새로 생긴 클라이밍장 다녀왔어요.\n초보 문제가 많고 샤워실 깨끗해요. 평일 저녁은 조금 붐벼요",
                        new int[]{8}, new String[]{"정보 감사해요! 이번 주에 가봐야겠어요"}),
                new Seed(1, Post.Category.FREE, null, "성수역 근처 새벽 6시에 여는 헬스장 아시는 분 계신가요?\n출근 전에 운동하고 싶어서요",
                        new int[]{5, 11}, new String[]{"역 3번 출구 쪽 24시간 헬스장 있어요!", "저도 새벽파예요 같이 해요 ㅎㅎ"}),
                new Seed(12, Post.Category.CERTIFY, "BADMINTON", "주말 복식 3연승 🏸 파트너 구해요!",
                        new int[]{}, new String[]{}),
                new Seed(14, Post.Category.FREE, "HIKING", "이번 주 토요일 아차산 날씨 좋대요. 해돋이 산행 모임 자리 남았어요 🌅",
                        new int[]{16}, new String[]{"저 갈게요!"}));

        for (Seed seed : seeds) {
            CommunityDtos.PostItem post = communityService.create(user(seed.author()).getId(),
                    new CommunityDtos.PostInput(seed.category(), seed.sport() == null ? null : sportId(seed.sport()),
                            seed.content()), List.of());
            for (int i = 0; i < seed.commenters().length; i++) {
                communityService.addComment(user(seed.commenters()[i]).getId(), post.id(),
                        new CommunityDtos.CommentInput(seed.comments()[i], null));
            }
        }
        log.info("데모 게시글 {}개를 만들었습니다.", seeds.size());
    }

    private long countExisting() {
        long count = 0;
        for (int i = 1; i <= DEMO_USER_COUNT; i++) {
            if (userRepository.existsByLoginId(loginId(i))) {
                count++;
            }
        }
        return count;
    }

    private User user(int index) {
        return userRepository.findByLoginId(loginId(index)).orElseThrow();
    }

    private Short sportId(String code) {
        return sportRepository.findAll().stream()
                .filter(sport -> sport.getCode().equals(code))
                .findFirst().orElseThrow().getId();
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
