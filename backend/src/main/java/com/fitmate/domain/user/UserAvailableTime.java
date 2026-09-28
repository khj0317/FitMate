package com.fitmate.domain.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(name = "user_available_times")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAvailableTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = DayOfWeekConverter.class)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    UserAvailableTime(User user, Slot slot) {
        this.user = user;
        this.dayOfWeek = slot.dayOfWeek();
        this.startTime = slot.startTime();
        this.endTime = slot.endTime();
    }

    public record Slot(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        public boolean overlaps(Slot other) {
            return dayOfWeek == other.dayOfWeek
                    && startTime.isBefore(other.endTime)
                    && other.startTime.isBefore(endTime);
        }
    }

    /** DB에는 ISO 기준 1(월) ~ 7(일)로 저장한다. */
    @Converter
    static class DayOfWeekConverter implements AttributeConverter<DayOfWeek, Short> {

        @Override
        public Short convertToDatabaseColumn(DayOfWeek dayOfWeek) {
            return dayOfWeek == null ? null : (short) dayOfWeek.getValue();
        }

        @Override
        public DayOfWeek convertToEntityAttribute(Short value) {
            return value == null ? null : DayOfWeek.of(value);
        }
    }
}
