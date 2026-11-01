package com.industrial.agent.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 序列化配置。
 * <p>
 * 前端展示的时间格式统一是 {@code yyyy-MM-dd HH:mm}（见 mock 数据），
 * 数据库里是 {@code DATETIME}，实体用 {@code LocalDateTime}。
 * 这里统一成前端能直接显示的字符串，省掉每个页面都写格式化函数。
 * <p>
 * <b>Long 的两种语义要分开处理</b>：
 * <ul>
 *   <li><b>ID</b>（{@code sys_user.id}、{@code roleId}、{@code workshopId}…）：
 *       将来若换成雪花 ID（&gt; 2^53），前端 JS Number 会精度丢失，所以按字符串输出。</li>
 *   <li><b>计数值</b>（{@code expiresIn}、{@code expiresAt}、{@code remainingSeconds}、
 *       {@code revokedBefore}）：前端要拿去做倒计时和大小比较，必须是数字。
 *       之前一刀切全转字符串，结果 {@code "expiresIn": "28800"}，
 *       {@code data.expiresIn * 1000} 全靠隐式转换，容易在严格比较处出错。</li>
 * </ul>
 * 实现方式：{@link Long}（包装类型）→ 字符串；{@code long}（原生）→ 数字。
 * 实体主键在 Lombok {@code @Data} 下都是包装类型，计时类字段则刻意声明成原生 {@code long}，
 * 两者天然分开，不需要按字段名打补丁。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";

    /**
     * ID 序列化器：{@code Long -> "123"}。
     * <p>
     * 只注册给 {@link Long}（包装类型），原生 {@code long} 不注册，因此计数值保持数字。
     */
    public static class LongIdSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(String.valueOf(value));
            }
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(TIME_PATTERN);

        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

            builder.serializerByType(LocalDate.class, new LocalDateSerializer(dateFormatter));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(dateFormatter));

            builder.serializerByType(LocalTime.class, new LocalTimeSerializer(timeFormatter));
            builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

            // 只对 Long 生效；long（原生）保持数字，避免 expiresIn 变成字符串
            builder.serializerByType(Long.class, new LongIdSerializer());
        };
    }

    /**
     * 暴露一个可注入的 ObjectMapper（Redis 序列化、AI 调用报文都用得到）。
     * <p>
     * 注意这里<b>不要</b>注入 {@code Jackson2ObjectMapperBuilderCustomizer}——
     * Spring Boot 自己也会注册一个 {@code standardJacksonObjectMapperBuilderCustomizer}，
     * 按类型注入会报 {@code expected single matching bean but found 2}（启动直接失败）。
     * 直接 {@code new} 一个并手动调用本类的方法即可。
     */
    @Bean("jacksonObjectMapper")
    public ObjectMapper jacksonObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        jacksonCustomizer().customize(builder);
        return builder.build();
    }
}