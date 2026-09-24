package dev.monitoring.worker.incident;

import static dev.monitoring.common.domain.MonitorStatus.DOWN;
import static dev.monitoring.common.domain.MonitorStatus.UNKNOWN;
import static dev.monitoring.common.domain.MonitorStatus.UP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.monitoring.common.domain.MonitorStatus;
import dev.monitoring.worker.incident.IncidentStateMachine.Event;
import dev.monitoring.worker.incident.IncidentStateMachine.State;
import dev.monitoring.worker.incident.IncidentStateMachine.Transition;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IncidentStateMachineTests {

    /** Feeds a sequence like "FFSF" (F = failure, S = success) and returns every event. */
    private static List<Event> run(String sequence, int failureThreshold, int recoveryThreshold,
                                   State[] finalState) {
        State state = new State(UNKNOWN, 0, 0);
        List<Event> events = new ArrayList<>();
        for (char c : sequence.toCharArray()) {
            Transition t = IncidentStateMachine.apply(state, c == 'S', failureThreshold,
                    recoveryThreshold);
            state = t.next();
            events.add(t.event());
        }
        finalState[0] = state;
        return events;
    }

    private static State finalState(String sequence, int failureThreshold, int recoveryThreshold) {
        State[] result = new State[1];
        run(sequence, failureThreshold, recoveryThreshold, result);
        return result[0];
    }

    private static long count(List<Event> events, Event type) {
        return events.stream().filter(e -> e == type).count();
    }

    @ParameterizedTest(name = "{0} with thresholds {1}/{2} -> {3}")
    @CsvSource({
            // sequence, failure threshold, recovery threshold, expected final status
            "S,          3, 2, UP",
            "F,          3, 2, UNKNOWN",
            "FF,         3, 2, UNKNOWN",
            "FFF,        3, 2, DOWN",
            "F,          1, 2, DOWN",
            "SFF,        3, 2, UP",
            "SFFF,       3, 2, DOWN",
            "FFSFF,      3, 2, UP",     // streak broken by a success
            "FFFS,       3, 2, DOWN",   // one success is not enough to recover
            "FFFSS,      3, 2, UP",
            "FFFSFSS,    3, 2, UP",     // recovery streak restarts after a failure
            "FFFSFS,     3, 2, DOWN",
            "FFFS,       3, 1, UP"})
    void finalStatusFollowsThresholds(String sequence, int failureThreshold,
                                      int recoveryThreshold, MonitorStatus expected) {
        assertThat(finalState(sequence, failureThreshold, recoveryThreshold).status())
                .isEqualTo(expected);
    }

    @Test
    void opensExactlyOneIncidentPerOutage() {
        State[] end = new State[1];
        List<Event> events = run("FFFFFFFFFF", 3, 2, end);

        assertThat(events.get(2)).isEqualTo(Event.INCIDENT_OPENED);
        assertThat(count(events, Event.INCIDENT_OPENED)).isOne();
        assertThat(end[0].consecutiveFailures()).isEqualTo(10);
    }

    @Test
    void resolvesOnRecoveryAndCanReopen() {
        State[] end = new State[1];
        List<Event> events = run("FFFSSFFF", 3, 2, end);

        assertThat(events).containsExactly(Event.NONE, Event.NONE, Event.INCIDENT_OPENED,
                Event.NONE, Event.INCIDENT_RESOLVED, Event.NONE, Event.NONE,
                Event.INCIDENT_OPENED);
        assertThat(end[0].status()).isEqualTo(DOWN);
    }

    @Test
    void firstSuccessMarksUpWithoutIncident() {
        Transition t = IncidentStateMachine.apply(new State(UNKNOWN, 0, 0), true, 3, 2);

        assertThat(t.next()).isEqualTo(new State(UP, 0, 1));
        assertThat(t.event()).isEqualTo(Event.NONE);
    }

    @Test
    void countersResetOnOppositeOutcome() {
        Transition afterFailure = IncidentStateMachine.apply(new State(UP, 0, 7), false, 3, 2);
        assertThat(afterFailure.next()).isEqualTo(new State(UP, 1, 0));

        Transition afterSuccess = IncidentStateMachine.apply(new State(DOWN, 9, 0), true, 3, 2);
        assertThat(afterSuccess.next()).isEqualTo(new State(DOWN, 0, 1));
    }
}
