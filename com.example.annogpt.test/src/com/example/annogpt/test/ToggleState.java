package com.example.annogpt.test;

public class ToggleState {
    private static ToggleState instance = new ToggleState();
    private boolean isEnabled = false; // 기본값은 비활성화

    private ToggleState() {}

    public static ToggleState getInstance() {
        return instance;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
        System.out.println("AnnoGPT Processing state changed to: " + isEnabled);
    }
    
    public void toggle() {
        this.isEnabled = !this.isEnabled;
        setEnabled(this.isEnabled);
    }
}