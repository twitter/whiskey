package com.twitter.internal.network.whiskey;

/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A SPDY Protocol SETTINGS Frame
 */
class SpdySettings {

    static final int MINOR_VERSION                  = 0;
    static final int UPLOAD_BANDWIDTH               = 1;
    static final int DOWNLOAD_BANDWIDTH             = 2;
    static final int ROUND_TRIP_TIME                = 3;
    static final int MAX_CONCURRENT_STREAMS         = 4;
    static final int CURRENT_CWND                   = 5;
    static final int DOWNLOAD_RETRANS_RATE          = 6;
    static final int INITIAL_WINDOW_SIZE            = 7;
    static final int CLIENT_CERTIFICATE_VECTOR_SIZE = 8;

    private boolean clear;
    private final Map<Integer, Setting> settingsMap = new TreeMap<Integer, Setting>();

    public Set<Integer> ids() {
        return settingsMap.keySet();
    }

    public boolean isSet(int id) {
        return settingsMap.containsKey(id);
    }

    public int getValue(int id) {
        if (settingsMap.containsKey(id)) {
            return settingsMap.get(id).getValue();
        } else {
            return -1;
        }
    }

    public SpdySettings setValue(int id, int value) {
        return setValue(id, value, false, false);
    }

    public SpdySettings setValue(int id, int value, boolean persistValue, boolean persisted) {
        if (id < 0 || id > SpdyCodecUtil.SPDY_SETTINGS_MAX_ID) {
            throw new IllegalArgumentException("Setting ID is not valid: " + id);
        }
        if (settingsMap.containsKey(id)) {
            Setting setting = settingsMap.get(id);
            setting.setValue(value);
            setting.setPersist(persistValue);
            setting.setPersisted(persisted);
        } else {
            settingsMap.put(id, new Setting(value, persistValue, persisted));
        }
        return this;
    }

    public SpdySettings removeValue(int id) {
        if (settingsMap.containsKey(id)) {
            settingsMap.remove(id);
        }
        return this;
    }

    public boolean isPersistValue(int id) {
        return settingsMap.containsKey(id) && settingsMap.get(id).isPersist();
    }

    public SpdySettings setPersistValue(int id, boolean persistValue) {
        if (settingsMap.containsKey(id)) {
            settingsMap.get(id).setPersist(persistValue);
        }
        return this;
    }

    public boolean isPersisted(int id) {
        return settingsMap.containsKey(id) && settingsMap.get(id).isPersisted();
    }

    public SpdySettings setPersisted(int id, boolean persisted) {
        if (settingsMap.containsKey(id)) {
            settingsMap.get(id).setPersisted(persisted);
        }
        return this;
    }

    public boolean clearPreviouslyPersistedSettings() {
        return clear;
    }

    public SpdySettings setClearPreviouslyPersistedSettings(boolean clear) {
        this.clear = clear;
        return this;
    }

    private Set<Map.Entry<Integer, Setting>> getSettings() {
        return settingsMap.entrySet();
    }

    private void appendSettings(StringBuilder buf) {
        for (Map.Entry<Integer, Setting> e: getSettings()) {
            Setting setting = e.getValue();
            buf.append("--> ");
            buf.append(e.getKey());
            buf.append(':');
            buf.append(setting.getValue());
            buf.append(" (persist value: ");
            buf.append(setting.isPersist());
            buf.append("; persisted: ");
            buf.append(setting.isPersisted());
            buf.append(')');
            buf.append("\n");
        }
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        appendSettings(buf);
        buf.setLength(buf.length() - 1);
        return buf.toString();
    }

    private static final class Setting {

        private int value;
        private boolean persist;
        private boolean persisted;

        Setting(int value, boolean persist, boolean persisted) {
            this.value = value;
            this.persist = persist;
            this.persisted = persisted;
        }

        int getValue() {
            return value;
        }

        void setValue(int value) {
            this.value = value;
        }

        boolean isPersist() {
            return persist;
        }

        void setPersist(boolean persist) {
            this.persist = persist;
        }

        boolean isPersisted() {
            return persisted;
        }

        void setPersisted(boolean persisted) {
            this.persisted = persisted;
        }
    }
}
