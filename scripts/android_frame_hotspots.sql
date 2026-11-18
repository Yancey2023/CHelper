-- Run with Perfetto trace_processor query -f scripts/android_frame_hotspots.sql TRACE.
-- FrameTimeline covers all recorded windows in the isolated application, including GPU / SF causes.
SELECT
    a.ts AS start_ns,
    ROUND(a.dur / 1000000.0, 3) AS actual_duration_ms,
    a.layer_name,
    a.present_type,
    a.jank_type,
    a.on_time_finish,
    a.gpu_composition
FROM actual_frame_timeline_slice AS a
JOIN process AS p USING (upid)
WHERE p.name = 'yancey.chelper.benchmark'
    AND a.jank_type NOT IN ('None', 'Non Animating')
ORDER BY a.ts;

-- Inclusive nested durations overlap; do not sum these rows to claim total CPU cost.
SELECT
    s.name,
    COUNT(*) AS calls,
    ROUND(SUM(s.dur) / 1000000.0, 3) AS inclusive_ms,
    ROUND(MAX(s.dur) / 1000000.0, 3) AS max_ms
FROM slice AS s
JOIN thread_track AS tt ON s.track_id = tt.id
JOIN thread AS t USING (utid)
JOIN process AS p USING (upid)
WHERE p.name = 'yancey.chelper.benchmark'
    AND t.tid = p.pid
    AND s.dur > 0
GROUP BY s.name
ORDER BY max_ms DESC
LIMIT 40;
