package com.voiceai.contact.domain.voice.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionState {
    public enum Mode { BOOKING, CONFIRMATION, QUERY, POST_CONFIRM, RESCHEDULE, END }

    public static final java.util.Set<String> ALLOWED_DEPARTMENTS = java.util.Set.of(
        "Orthopedic", "Cardiology", "Neurology", "Dermatology", "General Physician", "Pediatrics"
    );

    private String sessionId;
    private String patientName;
    private String department;
    private String suggestedDepartment;
    private double suggestedDeptConfidence;
    private String date;
    private java.time.LocalTime time;
    private String assignedDoctor;
    private boolean confirmed;
    private boolean greetingDone;   // true once the opening greeting has been sent
    private String lastAskedField;
    private int repeatCount;        // how many consecutive turns the same question has been asked
    private Mode mode;
    private List<Map<String, String>> messageHistory;

    public SessionState(String sessionId) {
        this.sessionId = sessionId;
        this.mode = Mode.BOOKING;
        this.messageHistory = new ArrayList<>();
        this.greetingDone = false;
        this.repeatCount = 0;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { 
        if (department == null) {
            this.department = null;
        } else if (ALLOWED_DEPARTMENTS.contains(department)) {
            this.department = department;
        }
    }

    public String getSuggestedDepartment() { return suggestedDepartment; }
    public void setSuggestedDepartment(String suggestedDepartment) { 
        if (suggestedDepartment == null) {
            this.suggestedDepartment = null;
        } else if (ALLOWED_DEPARTMENTS.contains(suggestedDepartment)) {
            this.suggestedDepartment = suggestedDepartment;
        }
    }

    public double getSuggestedDeptConfidence() { return suggestedDeptConfidence; }
    public void setSuggestedDeptConfidence(double suggestedDeptConfidence) { this.suggestedDeptConfidence = suggestedDeptConfidence; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public java.time.LocalTime getTime() { return time; }
    public void setTime(java.time.LocalTime time) { this.time = time; }

    public String getAssignedDoctor() { return assignedDoctor; }
    public void setAssignedDoctor(String assignedDoctor) { this.assignedDoctor = assignedDoctor; }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public boolean isGreetingDone() { return greetingDone; }
    public void setGreetingDone(boolean greetingDone) { this.greetingDone = greetingDone; }

    public int getRepeatCount() { return repeatCount; }
    public void incrementRepeatCount() { this.repeatCount++; }
    public void resetRepeatCount() { this.repeatCount = 0; }

    /** Resets repeatCount automatically when the field being asked changes. */
    public String getLastAskedField() { return lastAskedField; }
    public void setLastAskedField(String newField) {
        if (newField == null || !newField.equals(this.lastAskedField)) {
            this.repeatCount = 0;
        }
        this.lastAskedField = newField;
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public List<Map<String, String>> getMessageHistory() { return messageHistory; }

    public void appendMessage(String role, String content) {
        Map<String, String> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        this.messageHistory.add(msg);
    }
}
