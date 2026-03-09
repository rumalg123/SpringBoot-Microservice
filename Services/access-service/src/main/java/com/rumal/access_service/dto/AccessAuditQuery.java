package com.rumal.access_service.dto;

import java.util.UUID;

public final class AccessAuditQuery {

    private String targetType;
    private UUID targetId;
    private UUID vendorId;
    private String action;
    private String actorQuery;
    private String from;
    private String to;
    private Integer page;
    private Integer size;
    private Integer limit;

    public String targetType() {
        return targetType;
    }

    public AccessAuditQuery targetType(String targetType) {
        this.targetType = targetType;
        return this;
    }

    public UUID targetId() {
        return targetId;
    }

    public AccessAuditQuery targetId(UUID targetId) {
        this.targetId = targetId;
        return this;
    }

    public UUID vendorId() {
        return vendorId;
    }

    public AccessAuditQuery vendorId(UUID vendorId) {
        this.vendorId = vendorId;
        return this;
    }

    public String action() {
        return action;
    }

    public AccessAuditQuery action(String action) {
        this.action = action;
        return this;
    }

    public String actorQuery() {
        return actorQuery;
    }

    public AccessAuditQuery actorQuery(String actorQuery) {
        this.actorQuery = actorQuery;
        return this;
    }

    public String from() {
        return from;
    }

    public AccessAuditQuery from(String from) {
        this.from = from;
        return this;
    }

    public String to() {
        return to;
    }

    public AccessAuditQuery to(String to) {
        this.to = to;
        return this;
    }

    public Integer page() {
        return page;
    }

    public AccessAuditQuery page(Integer page) {
        this.page = page;
        return this;
    }

    public Integer size() {
        return size;
    }

    public AccessAuditQuery size(Integer size) {
        this.size = size;
        return this;
    }

    public Integer limit() {
        return limit;
    }

    public AccessAuditQuery limit(Integer limit) {
        this.limit = limit;
        return this;
    }
}
