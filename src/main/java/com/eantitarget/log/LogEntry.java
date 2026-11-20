package com.eantitarget.log;

import java.util.UUID;

public class LogEntry {
    public final String id;
    public final UUID uuid;
    public final String name;
    public final String check;
    public final String info;
    public final int vl;
    public final int threshold;
    public final long time;

    public LogEntry(String id, UUID uuid, String name, String check, String info, int vl, int threshold, long time) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.check = check;
        this.info = info;
        this.vl = vl;
        this.threshold = threshold;
        this.time = time;
    }
}
