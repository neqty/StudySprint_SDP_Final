package kz.studysprint.storage;

import kz.studysprint.model.*;
import kz.studysprint.patterns.builder.StudyPlanBuilder;
import kz.studysprint.patterns.factory.TaskCreators;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

// Simple readable text storage. Text values are Base64 encoded to allow tabs and newlines.
public class PlanRepository {
    private final Path path;
    public PlanRepository(Path path) { this.path = path; }
    public boolean exists() { return Files.exists(path); }
    private String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private String decoded(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
    public void save(List<StudyPlan> plans) throws IOException {
        StringBuilder data = new StringBuilder("STUDY_SPRINT_V1\n");
        for (StudyPlan p : plans) {
            data.append(String.join("\t", "PLAN", p.getId(), encoded(p.getName()),
                encoded(p.getStrategy()), String.valueOf(p.hasBreaks()), String.valueOf(p.hasTips()))).append("\n");
            // Optional record: old V1 plans have no duration and retain the 5-minute default.
            data.append("BREAK_MINUTES\t").append(p.getId()).append("\t")
                .append(p.getBreakMinutes()).append("\n");
            for (StudyTask t : p.getTasks()) {
                data.append(String.join("\t", "TASK", p.getId(), t.getId(), t.getType(), encoded(t.getTitle()),
                    encoded(t.getSubject()), String.valueOf(t.getMinutes()), String.valueOf(t.getPriority()),
                    encoded(t.getNotes()), String.valueOf(t.isCompleted()))).append("\n");
            }
            for (StudySession session : p.getSessions())
                data.append("SESSION\t").append(p.getId()).append("\t").append(session.getTaskId())
                    .append("\t").append(session.getPosition()).append("\n");
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, data, StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    public List<StudyPlan> load() throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !"STUDY_SPRINT_V1".equals(lines.get(0)))
            throw new IOException("Unsupported data file format.");
        Map<String, StudyPlan> plans = new LinkedHashMap<>();
        try {
            for (String line : lines) {
                String[] a = line.split("\t", -1);
                if (a[0].equals("PLAN") && a.length == 6) {
                    StudyPlan p = new StudyPlanBuilder().id(a[1]).name(decoded(a[2]))
                        .strategy(decoded(a[3])).breaks(Boolean.parseBoolean(a[4]))
                        .tips(Boolean.parseBoolean(a[5])).build();
                    plans.put(p.getId(), p);
                }
            }
            for (String line : lines) {
                String[] a = line.split("\t", -1);
                if (a[0].equals("BREAK_MINUTES")) {
                    if (a.length != 3) throw new IllegalArgumentException("Invalid break duration record.");
                    StudyPlan plan = plans.get(a[1]);
                    if (plan == null) throw new IllegalArgumentException("Break duration references missing plan.");
                    plan.setBreakMinutes(Integer.parseInt(a[2]));
                }
                if (a[0].equals("TASK") && a.length == 10) {
                    StudyPlan plan = plans.get(a[1]);
                    if (plan == null) throw new IllegalArgumentException("Task references missing plan.");
                    StudyTask task = TaskCreators.forType(a[3]).makeTask(a[2], decoded(a[4]), decoded(a[5]),
                        Integer.parseInt(a[6]), Integer.parseInt(a[7]), decoded(a[8]));
                    task.setCompleted(Boolean.parseBoolean(a[9]));
                    plan.getTasks().add(task);
                }
            }
            for (String line : lines) {
                String[] a = line.split("\t", -1);
                if (a[0].equals("SESSION") && a.length == 4) {
                    StudyPlan plan = plans.get(a[1]);
                    if (plan == null || plan.findTask(a[2]) == null)
                        throw new IllegalArgumentException("Session references missing task.");
                    plan.getSessions().add(new StudySession(a[2], Integer.parseInt(a[3])));
                }
            }
        } catch (RuntimeException e) {
            throw new IOException("Cannot read saved plans: " + e.getMessage(), e);
        }
        return new ArrayList<>(plans.values());
    }
}
