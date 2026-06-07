package com.hjan.actone.user.model;

public class UserPageRequest {

    private long current = 1;
    private long size = 10;
    private String keyword;

    public long getCurrent() {
        return Math.max(1, current);
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        return Math.min(100, Math.max(1, size));
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}

