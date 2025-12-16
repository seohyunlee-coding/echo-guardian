package com.example.imageblog;

public class StatsItem {
    private String name;
    private int count;

    public StatsItem(String name, int count) {
        this.name = name;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }
}

