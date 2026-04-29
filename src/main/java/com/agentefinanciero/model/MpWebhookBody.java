package com.agentefinanciero.model;

public class MpWebhookBody {

    private String type;
    private String action;
    private DataPayload data;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public DataPayload getData() { return data; }
    public void setData(DataPayload data) { this.data = data; }

    public static class DataPayload {
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
}
