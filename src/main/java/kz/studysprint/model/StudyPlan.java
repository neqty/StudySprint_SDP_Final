package kz.studysprint.model;

import java.util.ArrayList;
import java.util.List;

public class StudyPlan {
    private final String id;
    private String name;
    private String strategy;
    private boolean breaksEnabled;
    private boolean tipsEnabled;
    private int breakMinutes = 5;
    private final List<StudyTask> tasks = new ArrayList<>();
    private final List<StudySession> sessions = new ArrayList<>();

    public StudyPlan(String id, String name, String strategy, boolean breaksEnabled, boolean tipsEnabled) {
        this.id = id;
        setName(name);
        setStrategy(strategy);
        this.breaksEnabled = breaksEnabled;
        this.tipsEnabled = tipsEnabled;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Plan name is required.");
        this.name = name.trim();
    }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) {
        if (!"Priority first".equals(strategy) && !"Shortest first".equals(strategy))
            throw new IllegalArgumentException("Unknown scheduling method.");
        this.strategy = strategy;
    }
    public boolean hasBreaks() { return breaksEnabled; }
    public void setBreaksEnabled(boolean value) { breaksEnabled = value; }
    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int value) {
        if (value != 5 && value != 10)
            throw new IllegalArgumentException("Choose a 5- or 10-minute break.");
        breakMinutes = value;
    }
    public boolean hasTips() { return tipsEnabled; }
    public void setTipsEnabled(boolean value) { tipsEnabled = value; }
    public List<StudyTask> getTasks() { return tasks; }
    public List<StudySession> getSessions() { return sessions; }
    public StudyTask findTask(String id) {
        for (StudyTask task : tasks) if (task.getId().equals(id)) return task;
        return null;
    }
    @Override public String toString() { return name; }
}
