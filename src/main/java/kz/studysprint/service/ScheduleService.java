package kz.studysprint.service;

import kz.studysprint.model.*;
import kz.studysprint.patterns.strategy.*;
import kz.studysprint.patterns.decorator.*;
import java.util.*;

public class ScheduleService {
    public void generate(StudyPlan plan) {
        ScheduleStrategy strategy = "Priority first".equals(plan.getStrategy())
            ? new PriorityStrategy() : new ShortestStrategy();
        List<StudyTask> ordered = strategy.arrange(plan.getTasks());
        if (ordered.isEmpty()) throw new IllegalArgumentException("Add at least one unfinished task first.");
        plan.getSessions().clear();
        for (int i = 0; i < ordered.size(); i++)
            plan.getSessions().add(new StudySession(ordered.get(i).getId(), i + 1));
    }
    public SessionText decorate(StudyPlan plan, StudyTask task) {
        SessionText session = new BasicSessionText(task);
        if (plan.hasBreaks()) {
            session = plan.getBreakMinutes() == 10
                ? new LongBreakDecorator(session) : new BreakDecorator(session);
        }
        if (plan.hasTips()) session = new TipDecorator(session, task);
        return session;
    }
    public int totalMinutes(StudyPlan plan) {
        int total = 0;
        for (StudySession entry : plan.getSessions()) {
            StudyTask task = plan.findTask(entry.getTaskId());
            if (task != null) total += decorate(plan, task).totalMinutes();
        }
        return total;
    }
    public String display(StudyPlan plan) {
        if (plan.getSessions().isEmpty()) return "No schedule yet. Choose options and click Generate Schedule.";
        StringBuilder result = new StringBuilder();
        for (StudySession entry : plan.getSessions()) {
            StudyTask task = plan.findTask(entry.getTaskId());
            if (task != null) result.append(entry.getPosition()).append(". ")
                .append(decorate(plan, task).describe()).append("\n\n");
        }
        result.append("TOTAL: ").append(totalMinutes(plan)).append(" minutes");
        return result.toString();
    }
}
