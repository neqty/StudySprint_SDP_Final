
package kz.studysprint.patterns.factory;

public class TaskCreators {
    public static TaskCreator forType(String type) {
        if (type == null) {
            throw new IllegalArgumentException("Task type cannot be null.");
        }

        String normalizedType = type.trim();

        if ("Reading".equalsIgnoreCase(normalizedType)) {
            return new ReadingTaskCreator();
        }

        if ("Practice".equalsIgnoreCase(normalizedType)) {
            return new PracticeTaskCreator();
        }

        throw new IllegalArgumentException("Choose Reading or Practice.");
    }
}
