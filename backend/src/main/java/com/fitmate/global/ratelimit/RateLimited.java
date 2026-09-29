package com.fitmate.global.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드에 붙이면 windowSeconds 동안 limit번까지만 허용한다 (RateLimitInterceptor).
 * 로그인한 API는 사용자별로, 로그인 전 API(회원가입·지역 검색 등)는 IP별로 센다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /** Redis 키에 들어가는 이름. 같은 이름을 쓰는 API끼리 횟수를 공유한다 */
    String name();

    int limit();

    int windowSeconds();

    Key key() default Key.USER;

    enum Key {
        USER, IP
    }
}
