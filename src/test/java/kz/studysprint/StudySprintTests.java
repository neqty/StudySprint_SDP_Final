package kz.studysprint;

import kz.studysprint.model.*;
import kz.studysprint.patterns.facade.StudyFacade;
import kz.studysprint.patterns.builder.StudyPlanBuilder;
import kz.studysprint.patterns.factory.TaskCreators;
import kz.studysprint.patterns.adapter.*;
import kz.studysprint.patterns.observer.ActivityHistory;
import kz.studysprint.patterns.observer.PlanObserver;
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
        testObserverLifecycle(directory);
        System.out.println("ALL " + passed + " TESTS PASSED");
    }
    private static void testObserverLifecycle(Path directory) throws Exception {
        Path saved = directory.resolve("observer-data.txt");
        StudyFacade app = new StudyFacade(saved);
        ActivityHistory history = new ActivityHistory();
        app.addObserver(history);
        app.addObserver(history);
        app.renamePlan("Observer tests");
        check(history.getMessages().size() == 1, "duplicate subscription sends only one event");
        app.removeObserver(history);
        app.removeObserver(history);
        app.renamePlan("Unsubscribed");
        check(history.getMessages().size() == 1, "removed observer receives no further events");
        boolean nullRejected = false;
        try { app.addObserver(null); } catch (NullPointerException e) { nullRejected = true; }
        check(nullRejected, "null observer rejected at registration");

        int[] calls = {0};
        PlanObserver selfRemoving = new PlanObserver() {
            @Override public void onPlanChanged(String message) {
                calls[0]++;
                app.removeObserver(this);
                app.addObserver(history);
            }
        };
        ActivityHistory remaining = new ActivityHistory();
        app.addObserver(selfRemoving);
        app.addObserver(remaining);
        app.renamePlan("First event");
        check(calls[0] == 1 && remaining.getMessages().size() == 1
            && history.getMessages().size() == 1, "callbacks can change subscriptions without skipping observers");
        app.renamePlan("Second event");
        check(calls[0] == 1 && remaining.getMessages().size() == 2
            && history.getMessages().size() == 2, "subscription changes apply on the next event");

        boolean[] persisted = {false};
        PlanObserver persistenceCheck = message -> {
            try {
                persisted[0] = new StudyFacade(saved).getCurrent().getName().equals("Saved first");
            } catch (IOException e) { throw new AssertionError(e); }
        };
        app.addObserver(persistenceCheck);
        app.renamePlan("Saved first");
        check(persisted[0], "facade saves data before notifying observers");
        app.removeObserver(persistenceCheck);

        app.addObserver(selfRemoving);
        Path report = directory.resolve("observer-report.txt");
        app.saveReport(report, false, false);
        check(calls[0] == 2 && Files.exists(report)
            && remaining.getMessages().get(3).equals("Exported report: observer-report.txt"),
            "report export uses the same safe notification path");
        int before = remaining.getMessages().size();
        boolean failed = false;
        try { app.saveReport(directory, false, false); } catch (IOException e) { failed = true; }
        check(failed && remaining.getMessages().size() == before, "failed export sends no success event");
        Files.delete(saved);
        Files.createDirectory(saved);
        failed = false;
        try { app.renamePlan("Cannot save"); } catch (IOException e) { failed = true; }
        check(failed && remaining.getMessages().size() == before, "failed persistence sends no success event");

        ActivityHistory bounded = new ActivityHistory();
        for (int i = 0; i < 35; i++) bounded.onPlanChanged("Event " + i);
        check(bounded.getMessages().size() == 30 && bounded.getMessages().get(0).equals("Event 5"),
            "history retains the latest 30 events in order");
        bounded.getMessages().clear();
        check(bounded.getMessages().size() == 30, "history cannot be modified through its returned list");
    }

}
