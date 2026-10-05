package com.hansung.hsp.common;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TimeRangesTest {
    private Instant hour(int hour) { return Instant.parse("2030-01-01T%02d:00:00Z".formatted(hour)); }
    private TimeRange range(int start, int end) { return new TimeRange(hour(start), hour(end)); }

    @Test void subtractsClippedOverlappingAndAdjacentIntervals() {
        assertThat(TimeRanges.available(range(9, 18),
                List.of(range(8, 10), range(12, 14), range(13, 15), range(15, 16), range(17, 20))))
                .containsExactly(range(10, 12), range(16, 17));
    }
    @Test void boundaryOnlyAndOutsideIntervalsDoNotOccupyWindow() {
        assertThat(TimeRanges.available(range(10, 12), List.of(range(8, 10), range(12, 14))))
                .containsExactly(range(10, 12));
    }
    @Test void enclosingReservationLeavesNoAvailability() {
        assertThat(TimeRanges.available(range(10, 12), List.of(range(9, 13)))).isEmpty();
    }
    @Test void rejectsReversedOrEmptyIntervals() {
        assertThatThrownBy(() -> range(12, 10)).isInstanceOf(InvalidReservationTimeException.class);
        assertThatThrownBy(() -> range(10, 10)).isInstanceOf(InvalidReservationTimeException.class);
    }
}

