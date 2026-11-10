package kz.studysprint;

import kz.studysprint.model.*;
import kz.studysprint.patterns.decorator.*;
import kz.studysprint.storage.PlanRepository;
import kz.studysprint.patterns.facade.StudyFacade;
import kz.studysprint.patterns.builder.StudyPlanBuilder;
import kz.studysprint.patterns.factory.TaskCreators;
import kz.studysprint.patterns.adapter.*;
import kz.studysprint.patterns.observer.ActivityHistory;
import kz.studysprint.service.ScheduleService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

// Simple automatic business-logic tests; no testing library is needed.
public class StudySprintTests {
    private static int passed = 0;
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError("FAILED: " + name);
        passed++;
        System.out.println("PASS " + passed + " - " + name);
    }
    private static boolean rejects(Runnable r) {
        try { r.run(); return false; }
        catch (IllegalArgumentException e) { return true; }
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("studysprint-tests-");
        Path saved = directory.resolve("data.txt");
        StudyFacade app = new StudyFacade(saved);
        check(Files.exists(saved) && app.getCurrent().getTasks().size() == 3, "sample data on first start");
        ActivityHistory history = new ActivityHistory();
        app.addObserver(history);
        app.newPlan("My tests");
        check(app.getCurrent().getName().equals("My tests"), "create a plan using Builder");
        app.addTask("Reading", "Read chapter", "Java", 60, 5, "notes");
        app.addTask("Practice", "Solve exercises", "Java", 20, 1, "practice");
        check(app.getCurrent().getTasks().size() == 2, "create two task types with Factory Method");
        check(!app.getCurrent().getTasks().get(0).getStudyTip().equals(app.getCurrent().getTasks().get(1).getStudyTip()), "different task behaviours");
        app.setOptions("Priority first", false, false);
        app.generateSchedule();
        check(app.displaySchedule().indexOf("Read chapter") < app.displaySchedule().indexOf("Solve exercises"), "priority Strategy ordering");
        app.setOptions("Shortest first", false, false);
        app.generateSchedule();
        check(app.displaySchedule().indexOf("Solve exercises") < app.displaySchedule().indexOf("Read chapter"), "shortest Strategy ordering");
        check(app.totalMinutes() == 80, "schedule minutes without Decorator");
        app.setOptions("Shortest first", true, true);
        app.generateSchedule();
        check(app.totalMinutes() == 90 && app.displaySchedule().contains("Tip:"), "break and tip Decorators");
        check(history.getMessages().stream().anyMatch(m -> m.contains("Generated schedule")), "Observer gets schedule event");
        String id = app.getCurrent().getTasks().get(0).getId();
        app.toggleComplete(id);
        check(app.getCurrent().getTasks().get(0).isCompleted() && app.getCurrent().getSessions().isEmpty(), "mark done invalidates schedule");
        StudyFacade restarted = new StudyFacade(saved);
        restarted.selectPlan(app.getCurrent().getId());
        check(restarted.getCurrent().getTasks().get(0).isCompleted(), "persistent save and reload");
        check(restarted.getReport(true, true).contains("<html") && restarted.getReport(true, false).contains("[DONE]"), "Bridge report types and formats");
        check(rejects(() -> TaskCreators.forType("Reading").create("", "Java", 20, 3, "")), "empty title rejected");
        check(rejects(() -> TaskCreators.forType("Reading").create("Task", "Java", 1, 3, "")), "duration below 5 rejected");
        check(rejects(() -> TaskCreators.forType("Practice").create("Task", "Java", 20, 8, "")), "priority above 5 rejected");
        check(rejects(() -> new StudyPlanBuilder().name(" ").build()), "empty plan name rejected");
        Path csv = directory.resolve("tasks.csv");
        Files.writeString(csv, "Review,History,25,3,Reading\\nQuiz,Math,15,5,Practice\\n".replace("\\n", "\n"), StandardCharsets.UTF_8);
        restarted.importLegacyCsv(csv);
        check(restarted.getCurrent().getTasks().size() == 4, "Adapter imports legacy CSV");
        Files.writeString(csv, "Bad row,Math,-9,3,Practice\n");
        int before = restarted.getCurrent().getTasks().size();
        boolean failed = false;
        try { restarted.importLegacyCsv(csv); } catch (IOException e) { failed = true; }
        check(failed && restarted.getCurrent().getTasks().size() == before, "invalid CSV rejected without partial import");
        String updateId = restarted.getCurrent().getTasks().get(1).getId();
        restarted.updateTask(updateId, "Reading", "Updated", "Java", 35, 4, "new");
        check(restarted.getCurrent().findTask(updateId).getTitle().equals("Updated"), "edit a task");
        restarted.removeTask(updateId);
        check(restarted.getCurrent().findTask(updateId) == null, "delete a task");
        Path export = directory.resolve("result.html");
        restarted.saveReport(export, true, true);
        check(Files.readString(export).contains("Updated") == false, "export correct state to HTML");
        StudyFacade again = new StudyFacade(saved);
        again.selectPlan(restarted.getCurrent().getId());
        check(again.getCurrent().getTasks().size() == 3, "edits and deletes persist");
        check(TaskCreators.forType("  rEaDiNg ").create("Read", "Java", 20, 3, "") instanceof ReadingTask,
            "Factory Method normalizes Reading type");
        check(TaskCreators.forType(" PRACTICE ").create("Solve", "Java", 20, 3, "") instanceof PracticeTask,
            "Factory Method normalizes Practice type");
        check(rejects(() -> TaskCreators.forType(null)) && rejects(() -> TaskCreators.forType(" "))
            && rejects(() -> TaskCreators.forType("Unknown")), "Factory Method rejects invalid types");
        testBreaks(directory);
        System.out.println("ALL " + passed + " TESTS PASSED");
    }
    private static void testBreaks(Path directory) throws Exception {
        StudyTask task = TaskCreators.forType("Reading").create("Read", "Java", 20, 3, "");
        SessionText basic = new BasicSessionText(task);
        SessionText shortBreak = new BreakDecorator(basic);
        SessionText longBreak = new LongBreakDecorator(basic);
        check(shortBreak.totalMinutes() == 25 && shortBreak.describe().equals("Read (20 min) + 5 min break"),
            "BreakDecorator retains five minutes and description");
        check(longBreak.totalMinutes() == 30, "LongBreakDecorator adds ten minutes");
        check(longBreak.describe().equals("Read (20 min) + 10 min break"), "LongBreakDecorator description");
        SessionText withTip = new TipDecorator(longBreak, task);
        check(withTip.totalMinutes() == 30 && withTip.describe().equals(longBreak.describe() + "\n     Tip: " + task.getStudyTip()),
            "long break composes with TipDecorator");
        check(basic.totalMinutes() == 20 && basic.describe().equals("Read (20 min)"), "decorators leave wrapped session unchanged");
        StudyPlan plan = new StudyPlanBuilder().name("Defaults").build();
        check(plan.getBreakMinutes() == 5, "existing Builder defaults to five-minute breaks");
        check(rejects(() -> plan.setBreakMinutes(0)) && rejects(() -> plan.setBreakMinutes(15))
            && plan.getBreakMinutes() == 5, "unsupported break durations rejected");

        Path file = directory.resolve("breaks.txt");
        StudyFacade app = new StudyFacade(file);
        app.newPlan("Break tests");
        String planId = app.getCurrent().getId();
        app.addTask("Reading", "Read", "Java", 20, 3, "");
        app.addTask("Practice", "Solve", "Java", 30, 4, "");
        app.setOptions("Priority first", true, true, 10);
        app.generateSchedule();
        check(app.totalMinutes() == 70 && app.displaySchedule().contains("TOTAL: 70 minutes")
            && app.displaySchedule().contains("+ 10 min break") && app.displaySchedule().contains("Tip:")
            && !app.displaySchedule().contains("+ 5 min break"), "schedule uses long breaks and tips");
        StudyFacade loaded = new StudyFacade(file);
        loaded.selectPlan(planId);
        check(loaded.getCurrent().getBreakMinutes() == 10 && loaded.totalMinutes() == 70
            && loaded.displaySchedule().equals(app.displaySchedule()), "long break setting and schedule survive restart");
        check(loaded.getPlans().get(0).getBreakMinutes() == 5, "break duration belongs to each plan");
        loaded.setOptions("Priority first", false, true);
        check(loaded.getCurrent().getSessions().isEmpty() && loaded.getCurrent().getBreakMinutes() == 10,
            "old options API preserves duration and invalidates schedule");
        loaded.generateSchedule();
        check(loaded.totalMinutes() == 50 && !loaded.displaySchedule().contains("min break")
            && loaded.displaySchedule().contains("Tip:"), "disabled breaks add no time while tips remain");
        StudyFacade disabled = new StudyFacade(file);
        disabled.selectPlan(planId);
        check(!disabled.getCurrent().hasBreaks() && disabled.getCurrent().getBreakMinutes() == 10
            && disabled.totalMinutes() == 50, "disabled break preference survives restart");
        disabled.setOptions("Shortest first", true, false, 5);
        disabled.generateSchedule();
        check(disabled.totalMinutes() == 60 && disabled.displaySchedule().contains("+ 5 min break")
            && !disabled.displaySchedule().contains("Tip:"), "switch back to short breaks without tips");
        disabled.setOptions("Shortest first", true, false, 10);
        disabled.generateSchedule();
        check(disabled.totalMinutes() == 70 && !disabled.displaySchedule().contains("Tip:"), "long breaks work without tips");
        check(rejects(() -> { try { disabled.setOptions("Priority first", false, true, 7); }
            catch (IOException e) { throw new RuntimeException(e); } })
            && disabled.totalMinutes() == 70 && disabled.getCurrent().getStrategy().equals("Shortest first"),
            "invalid options do not partially change plan");

        // Literal original V1 records: no BREAK_MINUTES setting, including an existing session.
        Path legacy = directory.resolve("legacy.txt");
        Files.writeString(legacy, "STUDY_SPRINT_V1\n"
            + "PLAN\told\tT2xk\tUHJpb3JpdHkgZmlyc3Q=\ttrue\ttrue\n"
            + "PLAN\toff\tT2Zm\tUHJpb3JpdHkgZmlyc3Q=\tfalse\tfalse\n"
            + "TASK\told\ttask\tReading\tUmVhZA==\tSmF2YQ==\t20\t3\t\tfalse\n"
            + "SESSION\told\ttask\t1\n");
        StudyFacade old = new StudyFacade(legacy);
        check(old.getCurrent().getBreakMinutes() == 5 && old.totalMinutes() == 25
            && old.displaySchedule().contains("+ 5 min break") && old.displaySchedule().contains("Tip:"),
            "original V1 plan retains five-minute schedule and tips");
        old.selectPlan("off");
        check(!old.getCurrent().hasBreaks() && !old.getCurrent().hasTips()
            && old.getCurrent().getBreakMinutes() == 5, "original disabled settings remain disabled");
        old.selectPlan("old");
        old.setOptions("Priority first", true, true, 10);
        old.generateSchedule();
        StudyFacade migrated = new StudyFacade(legacy);
        check(migrated.totalMinutes() == 30 && migrated.getCurrent().getTasks().size() == 1,
            "original plan can save and reload ten-minute breaks");
        Files.writeString(legacy, Files.readString(legacy).replace("BREAK_MINUTES\told\t10", "BREAK_MINUTES\told\t7"));
        boolean invalid = false;
        try { new PlanRepository(legacy).load(); } catch (IOException e) { invalid = true; }
        check(invalid, "invalid saved break duration rejected");
    }
}
