package com.yun.aiinterview.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.Container.class)
public @interface RateLimit {

    enum Dimension {GLOBAL, IP}

    Dimension dimension() default Dimension.GLOBAL;

    int count();

    /**
     * 窗口时长，单位：秒（默认 60 秒，即一分钟窗口）
     */
    long interval() default 60;

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Container {
        RateLimit[] value();
    }
}
