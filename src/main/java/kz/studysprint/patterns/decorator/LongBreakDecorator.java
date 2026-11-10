package kz.studysprint.patterns.decorator;

public class LongBreakDecorator implements SessionText {
    private final SessionText inner;

    public LongBreakDecorator(SessionText inner) {
        this.inner = inner;
    }

    @Override
    public String describe() {
        return inner.describe() + " + 10 min break";
    }

    @Override
    public int totalMinutes() {
        return inner.totalMinutes() + 10;
    }
}
