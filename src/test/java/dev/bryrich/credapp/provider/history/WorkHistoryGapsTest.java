package dev.bryrich.credapp.provider.history;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkHistoryGapsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    @Test
    void sixMonthsOrMoreBetweenJobsIsAGap_lessIsNot() {
        List<WorkHistoryEntry> work = List.of(
                job("2015-07-01", "2018-06-30"),
                job("2018-10-01", "2020-01-31"),   // 3 months after the first: fine
                job("2020-12-01", null));           // 10 months after the second: a gap
        assertThat(WorkHistoryGaps.find(work, List.of(), TODAY))
                .singleElement()
                .satisfies(gap -> {
                    assertThat(gap.from()).isEqualTo(LocalDate.of(2020, 1, 31));
                    assertThat(gap.to()).isEqualTo(LocalDate.of(2020, 12, 1));
                    assertThat(gap.describe()).isEqualTo("Jan 2020 to Dec 2020 (10 months)");
                });
    }

    @Test
    void explainedTimeAwayAndTrainingCloseAGap() {
        WorkHistoryEntry away = new WorkHistoryEntry(WorkEntryType.GAP, LocalDate.parse("2020-02-01"));
        away.setEndDate(LocalDate.parse("2020-11-30"));
        List<WorkHistoryEntry> work = List.of(job("2015-07-01", "2020-01-31"), away, job("2020-12-01", null));
        assertThat(WorkHistoryGaps.find(work, List.of(), TODAY)).isEmpty();

        ProviderTraining fellowship = new ProviderTraining(TrainingType.FELLOWSHIP, "U of U", LocalDate.parse("2020-02-01"));
        fellowship.setEndDate(LocalDate.parse("2020-11-30"));
        assertThat(WorkHistoryGaps.find(List.of(job("2015-07-01", "2020-01-31"), job("2020-12-01", null)),
                List.of(fellowship), TODAY)).isEmpty();
    }

    @Test
    void notWorkingNowForSixMonthsIsAGapStillGoing() {
        assertThat(WorkHistoryGaps.find(List.of(job("2015-07-01", "2025-12-31")), List.of(), TODAY))
                .singleElement()
                .satisfies(gap -> {
                    assertThat(gap.ongoing()).isTrue();
                    assertThat(gap.describe()).isEqualTo("Dec 2025 to now (8 months)");
                });
        assertThat(WorkHistoryGaps.find(List.of(), List.of(), TODAY)).isEmpty();
    }

    @Test
    void overlappingJobsDontMakeGaps() {
        List<WorkHistoryEntry> work = List.of(
                job("2010-01-01", "2025-01-01"),
                job("2012-01-01", "2013-01-01"),   // inside the first
                job("2025-03-01", null));
        assertThat(WorkHistoryGaps.find(work, List.of(), TODAY)).isEmpty();
    }

    private static WorkHistoryEntry job(String start, String end) {
        WorkHistoryEntry entry = new WorkHistoryEntry(WorkEntryType.JOB, LocalDate.parse(start));
        entry.setEmployer("Clinic");
        entry.setEndDate(end == null ? null : LocalDate.parse(end));
        return entry;
    }
}
