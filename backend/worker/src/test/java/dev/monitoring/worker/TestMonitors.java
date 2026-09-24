package dev.monitoring.worker;

import dev.monitoring.common.domain.Monitor;
import dev.monitoring.common.domain.MonitorType;
import dev.monitoring.common.repository.MonitorRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Helpers for inserting monitors in a given scheduling state. */
public final class TestMonitors {

    private TestMonitors() {
    }

    public static UUID due(MonitorRepository repo, String name, int secondsOverdue) {
        Monitor m = new Monitor(name, MonitorType.HTTP, "https://example.com/" + name, 60, 5000);
        m.setNextCheckAt(Instant.now().minusSeconds(secondsOverdue));
        return repo.save(m).getId();
    }

    public static List<UUID> due(MonitorRepository repo, int count) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(due(repo, "m" + i, 60));
        }
        return ids;
    }

    public static UUID notDue(MonitorRepository repo, String name) {
        Monitor m = new Monitor(name, MonitorType.HTTP, "https://example.com/" + name, 60, 5000);
        m.setNextCheckAt(Instant.now().plusSeconds(3600));
        return repo.save(m).getId();
    }

    public static UUID disabledDue(MonitorRepository repo, String name) {
        Monitor m = new Monitor(name, MonitorType.HTTP, "https://example.com/" + name, 60, 5000);
        m.setNextCheckAt(Instant.now().minusSeconds(60));
        m.setEnabled(false);
        return repo.save(m).getId();
    }
}
