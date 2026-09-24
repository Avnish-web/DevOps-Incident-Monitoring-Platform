-- Why an incident ended: the target recovered, or monitoring of it was paused/changed while it
-- was down. Keeps "resolved" honest: only RECOVERED means the target came back.

ALTER TABLE incidents ADD COLUMN resolution varchar(16);

-- Backfill for any incidents resolved before this column existed.
UPDATE incidents SET resolution = 'RECOVERED' WHERE resolved_at IS NOT NULL;

ALTER TABLE incidents
    ADD CONSTRAINT ck_incidents_resolution
        CHECK (resolution IN ('RECOVERED', 'MONITOR_PAUSED', 'MONITOR_CHANGED')),
    ADD CONSTRAINT ck_incidents_resolution_iff_resolved
        CHECK ((resolved_at IS NULL) = (resolution IS NULL));
