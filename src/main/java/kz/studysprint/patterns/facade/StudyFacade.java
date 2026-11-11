package kz.studysprint.patterns.facade;

import kz.studysprint.model.*;
import kz.studysprint.storage.PlanRepository;
import kz.studysprint.service.ScheduleService;
import kz.studysprint.patterns.builder.StudyPlanBuilder;
import kz.studysprint.patterns.factory.TaskCreators;
import kz.studysprint.patterns.adapter.*;
import kz.studysprint.patterns.bridge.*;
import kz.studysprint.patterns.observer.PlanObserver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

// FACADE: UI uses one simple class instead of storage, factory, scheduler, import and reports.
public class StudyFacade {
    private final PlanRepository repository;
    private final ScheduleService scheduler = new ScheduleService();
    private final TaskImporter importer = new LegacyCsvAdapter(new OldCsvReader());
    private final List<PlanObserver> observers = new ArrayList<>();
    private final List<StudyPlan> plans;
    private StudyPlan current;

    public StudyFacade(Path dataFile) throws IOException {
        repository = new PlanRepository(dataFile);
        if (repository.exists()) plans = repository.load();
        else {
            plans = new ArrayList<>();
            StudyPlan sample = new StudyPlanBuilder().name("Exam preparation").strategy("Priority first")
                .breaks(true).tips(true).build();
            sample.getTasks().add(TaskCreators.forType("Reading").create("Review graphs", "Algorithms", 45, 5, "Chapter 6"));
            sample.getTasks().add(TaskCreators.forType("Practice").create("Solve SQL tasks", "Databases", 30, 4, "5 exercises"));
            sample.getTasks().add(TaskCreators.forType("Reading").create("Read Java notes", "Programming", 20, 2, "Design patterns"));
            plans.add(sample);
            repository.save(plans);
        }
        if (plans.isEmpty()) {
            StudyPlan basic = new StudyPlanBuilder().name("My study plan").build();
            plans.add(basic); repository.save(plans);
        }
        current = plans.get(0);
    }
    /** Registers a non-null observer once, preserving notification order. */
    public void addObserver(PlanObserver observer) {
        Objects.requireNonNull(observer, "Observer must not be null.");
        if (!observers.contains(observer)) observers.add(observer);
    }
    public void removeObserver(PlanObserver observer) { observers.remove(observer); }

    // Subscription changes inside a callback take effect on the next event.
    private void notifyObservers(String message) {
        for (PlanObserver observer : new ArrayList<>(observers)) observer.onPlanChanged(message);
    }
    private void changed(String message) throws IOException {
        repository.save(plans);
        notifyObservers(message);
    }
    public List<StudyPlan> getPlans() { return new ArrayList<>(plans); }
    public StudyPlan getCurrent() { return current; }
    public void selectPlan(String id) {
        for (StudyPlan plan : plans) if (plan.getId().equals(id)) { current = plan; return; }
        throw new IllegalArgumentException("Plan not found.");
    }
    public void newPlan(String name) throws IOException {
        StudyPlan plan = new StudyPlanBuilder().name(name).build();
        plans.add(plan); current = plan; changed("Created plan: " + name);
    }
    public void renamePlan(String name) throws IOException {
        current.setName(name); changed("Renamed plan: " + name);
    }
    public void deletePlan() throws IOException {
        if (plans.size() == 1) throw new IllegalArgumentException("Keep at least one plan.");
        String name = current.getName();
        plans.remove(current); current = plans.get(0); changed("Deleted plan: " + name);
    }
    public void addTask(String type, String title, String subject, int minutes, int priority, String notes) throws IOException {
        StudyTask task = TaskCreators.forType(type).create(title, subject, minutes, priority, notes);
        current.getTasks().add(task); current.getSessions().clear();
        changed("Added " + type.toLowerCase() + " task: " + title);
    }
    public void updateTask(String id, String type, String title, String subject, int minutes, int priority, String notes) throws IOException {
        StudyTask old = requireTask(id);
        StudyTask replacement = TaskCreators.forType(type).makeTask(id, title, subject, minutes, priority, notes);
        replacement.setCompleted(old.isCompleted());
        int index = current.getTasks().indexOf(old);
        current.getTasks().set(index, replacement);
        current.getSessions().clear();
        changed("Edited task: " + title);
    }
    public void removeTask(String id) throws IOException {
        StudyTask task = requireTask(id);
        current.getTasks().remove(task); current.getSessions().clear();
        changed("Deleted task: " + task.getTitle());
    }
    public void toggleComplete(String id) throws IOException {
        StudyTask task = requireTask(id);
        task.setCompleted(!task.isCompleted()); current.getSessions().clear();
        changed((task.isCompleted() ? "Completed: " : "Reopened: ") + task.getTitle());
    }
    private StudyTask requireTask(String id) {
        StudyTask task = current.findTask(id);
        if (task == null) throw new IllegalArgumentException("Select a valid task.");
        return task;
    }
    public void setOptions(String strategy, boolean breaks, boolean tips) throws IOException {
        current.setStrategy(strategy);
        current.setBreaksEnabled(breaks); current.setTipsEnabled(tips);
        current.getSessions().clear(); changed("Changed schedule options");
    }
    public void generateSchedule() throws IOException {
        scheduler.generate(current); changed("Generated schedule with " + current.getStrategy());
    }
    public String displaySchedule() { return scheduler.display(current); }
    public int totalMinutes() { return scheduler.totalMinutes(current); }
    public void importLegacyCsv(Path path) throws IOException {
        List<StudyTask> items = importer.importTasks(path);
        current.getTasks().addAll(items); current.getSessions().clear();
        changed("Imported " + items.size() + " task(s) from CSV");
    }
    public String getReport(boolean detailed, boolean html) {
        ReportFormat format = html ? new HtmlFormat() : new PlainTextFormat();
        StudyReport report = detailed ? new DetailedReport(format) : new SummaryReport(format);
        return report.create(current, scheduler);
    }
    public void saveReport(Path file, boolean detailed, boolean html) throws IOException {
        Files.writeString(file, getReport(detailed, html), StandardCharsets.UTF_8);
        notifyObservers("Exported report: " + file.getFileName());
    }
}
