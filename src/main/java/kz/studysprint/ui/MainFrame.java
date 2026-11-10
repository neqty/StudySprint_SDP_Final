package kz.studysprint.ui;

import kz.studysprint.model.*;
import kz.studysprint.patterns.facade.StudyFacade;
import kz.studysprint.patterns.observer.ActivityHistory;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.*;

public class MainFrame extends JFrame {
    private final StudyFacade app;
    private final ActivityHistory history = new ActivityHistory();
    private final JComboBox<StudyPlan> planPicker = new JComboBox<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(
        new Object[]{"Task", "Subject", "Type", "Min", "Priority", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable taskTable = new JTable(tableModel);
    private final JTextArea scheduleText = new JTextArea();
    private final JTextArea reportText = new JTextArea();
    private final JTextArea historyText = new JTextArea();
    private final JComboBox<String> strategyPicker = new JComboBox<>(new String[]{"Priority first", "Shortest first"});
    private final JCheckBox breaksBox = new JCheckBox("Add breaks");
    private final JComboBox<Integer> breakMinutesPicker = new JComboBox<>(new Integer[]{5, 10});
    private final JCheckBox tipsBox = new JCheckBox("Show study tips");
    private final JComboBox<String> reportTypePicker = new JComboBox<>(new String[]{"Summary", "Detailed"});
    private final JComboBox<String> formatPicker = new JComboBox<>(new String[]{"TXT", "HTML"});
    private final JLabel status = new JLabel("Ready");
    private boolean updating;

    public MainFrame(StudyFacade app) {
        super("Study Sprint - Study Planner");
        this.app = app;
        setSize(1200, 740);
        setMinimumSize(new Dimension(970, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        buildUI();
        app.addObserver(history);
        app.addObserver(message -> {
            refresh();
            status.setText("  " + message);
        });
        refresh();
    }

    private JButton button(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setFont(new Font("SansSerif", Font.PLAIN, 13));
        b.addActionListener(e -> action.run());
        return b;
    }
    private JPanel horizontal(Component... items) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        for (Component item : items) panel.add(item);
        return panel;
    }
    private JScrollPane textBox(JTextArea area) {
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setMargin(new Insets(16, 16, 16, 16));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return new JScrollPane(area);
    }
    private JPanel titled(String title, JComponent main, JComponent tools) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        if (tools != null) panel.add(tools, BorderLayout.NORTH);
        panel.add(main, BorderLayout.CENTER);
        return panel;
    }
    private void buildUI() {
        setLayout(new BorderLayout(10, 10));
        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(12, 15, 7, 15));
        JLabel heading = new JLabel("STUDY SPRINT");
        heading.setFont(new Font("SansSerif", Font.BOLD, 25));
        heading.setForeground(new Color(22, 58, 107));
        top.add(heading, BorderLayout.WEST);
        planPicker.setPreferredSize(new Dimension(180, 30));
        planPicker.addActionListener(e -> {
            if (!updating && planPicker.getSelectedItem() != null) {
                app.selectPlan(((StudyPlan) planPicker.getSelectedItem()).getId());
                refresh();
            }
        });
        top.add(horizontal(new JLabel("Plan:"), planPicker,
            button("New", () -> perform(() -> {
                String name = JOptionPane.showInputDialog(this, "Plan name:");
                if (name != null) app.newPlan(name);
            })),
            button("Rename", () -> perform(() -> {
                String name = JOptionPane.showInputDialog(this, "New plan name:", app.getCurrent().getName());
                if (name != null) app.renamePlan(name);
            })),
            button("Delete plan", () -> perform(() -> {
                if (confirm("Delete this plan and all its tasks?")) app.deletePlan();
            }))), BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        taskTable.setRowHeight(29);
        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane tableScroll = new JScrollPane(taskTable);
        JPanel tasks = titled("Tasks", tableScroll, horizontal(
            button("Add task", () -> editTask(false)),
            button("Edit", () -> editTask(true)),
            button("Done / Undo", () -> perform(() -> app.toggleComplete(selectedTask().getId()))),
            button("Delete", () -> perform(() -> {
                StudyTask t = selectedTask();
                if (confirm("Delete '" + t.getTitle() + "'?")) app.removeTask(t.getId());
            })),
            button("Import CSV", this::importCsv)));
        tasks.setPreferredSize(new Dimension(595, 500));

        JTabbedPane tabs = new JTabbedPane();
        JPanel schedulePanel = new JPanel(new BorderLayout(6, 6));
        JPanel scheduleTools = new JPanel(new GridLayout(3, 1));
        scheduleTools.add(horizontal(new JLabel("Order:"), strategyPicker, tipsBox));
        scheduleTools.add(horizontal(breaksBox, breakMinutesPicker, new JLabel("minutes per task")));
        breaksBox.addActionListener(e -> breakMinutesPicker.setEnabled(breaksBox.isSelected()));
        scheduleTools.add(horizontal(
            button("Apply options", () -> perform(() -> app.setOptions(
                (String) strategyPicker.getSelectedItem(), breaksBox.isSelected(), tipsBox.isSelected(), (Integer) breakMinutesPicker.getSelectedItem()))),
            button("Generate schedule", () -> perform(() -> {
                app.setOptions((String) strategyPicker.getSelectedItem(), breaksBox.isSelected(), tipsBox.isSelected(), (Integer) breakMinutesPicker.getSelectedItem());
                app.generateSchedule();
            }))));
        schedulePanel.add(scheduleTools, BorderLayout.NORTH);
        schedulePanel.add(textBox(scheduleText), BorderLayout.CENTER);
        tabs.addTab("Schedule", schedulePanel);

        JPanel reportPanel = new JPanel(new BorderLayout(6, 6));
        reportPanel.add(horizontal(new JLabel("Report:"), reportTypePicker, formatPicker,
            button("Preview", this::previewReport), button("Save file", this::saveReport)), BorderLayout.NORTH);
        reportPanel.add(textBox(reportText), BorderLayout.CENTER);
        tabs.addTab("Reports", reportPanel);
        tabs.addTab("Activity", textBox(historyText));
        JPanel right = titled("Plan details", tabs, null);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tasks, right);
        split.setResizeWeight(0.52);
        split.setDividerLocation(610);
        split.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
        add(split, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(5, 18, 10, 18));
        footer.add(status, BorderLayout.WEST);
        JLabel hint = new JLabel("Data is saved automatically   |   Java Swing");
        hint.setForeground(Color.GRAY);
        footer.add(hint, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }
    private void refresh() {
        updating = true;
        String oldId = getSelectedTaskId();
        planPicker.removeAllItems();
        for (StudyPlan p : app.getPlans()) planPicker.addItem(p);
        for (int i = 0; i < planPicker.getItemCount(); i++) {
            if (planPicker.getItemAt(i).getId().equals(app.getCurrent().getId())) planPicker.setSelectedIndex(i);
        }
        StudyPlan plan = app.getCurrent();
        tableModel.setRowCount(0);
        for (StudyTask t : plan.getTasks()) {
            tableModel.addRow(new Object[]{t.getTitle(), t.getSubject(), t.getType(), t.getMinutes(),
                t.getPriority(), t.isCompleted() ? "Done" : "To do"});
        }
        if (oldId != null) {
            for (int i = 0; i < plan.getTasks().size(); i++) {
                if (plan.getTasks().get(i).getId().equals(oldId)) taskTable.setRowSelectionInterval(i, i);
            }
        }
        strategyPicker.setSelectedItem(plan.getStrategy());
        breaksBox.setSelected(plan.hasBreaks()); tipsBox.setSelected(plan.hasTips());
        breakMinutesPicker.setSelectedItem(plan.getBreakMinutes());
        breakMinutesPicker.setEnabled(plan.hasBreaks());
        scheduleText.setText(app.displaySchedule());
        reportText.setText(app.getReport(isDetailed(), isHtml()));
        StringBuilder log = new StringBuilder();
        for (String message : history.getMessages()) log.append("• ").append(message).append("\n\n");
        historyText.setText(log.length() == 0 ? "Actions will appear here when you edit your plan." : log.toString());
        long done = plan.getTasks().stream().filter(StudyTask::isCompleted).count();
        setTitle("Study Sprint - " + plan.getName() + " (" + done + "/" + plan.getTasks().size() + " complete)");
        updating = false;
    }
    private String getSelectedTaskId() {
        int row = taskTable.getSelectedRow();
        if (row < 0 || row >= app.getCurrent().getTasks().size()) return null;
        return app.getCurrent().getTasks().get(row).getId();
    }
    private StudyTask selectedTask() {
        String id = getSelectedTaskId();
        if (id == null) throw new IllegalArgumentException("Select a task in the table first.");
        return app.getCurrent().findTask(id);
    }
    private interface Action { void run() throws Exception; }
    private void perform(Action action) {
        try { action.run(); }
        catch (Exception e) { JOptionPane.showMessageDialog(this, e.getMessage(), "Check your input", JOptionPane.ERROR_MESSAGE); }
    }
    private boolean confirm(String question) {
        return JOptionPane.showConfirmDialog(this, question, "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
    private void editTask(boolean edit) {
        perform(() -> {
            StudyTask old = edit ? selectedTask() : null;
            TaskDialog.Values values = TaskDialog.show(this, old);
            if (values == null) return;
            if (edit) app.updateTask(old.getId(), values.type, values.title, values.subject,
                values.minutes, values.priority, values.notes);
            else app.addTask(values.type, values.title, values.subject,
                values.minutes, values.priority, values.notes);
        });
    }
    private void importCsv() {
        JFileChooser chooser = new JFileChooser(new File("samples"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            perform(() -> app.importLegacyCsv(chooser.getSelectedFile().toPath()));
    }
    private boolean isDetailed() { return "Detailed".equals(reportTypePicker.getSelectedItem()); }
    private boolean isHtml() { return "HTML".equals(formatPicker.getSelectedItem()); }
    private void previewReport() { reportText.setText(app.getReport(isDetailed(), isHtml())); }
    private void saveReport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(isHtml() ? "study-report.html" : "study-report.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
            perform(() -> app.saveReport(chooser.getSelectedFile().toPath(), isDetailed(), isHtml()));
    }
}
