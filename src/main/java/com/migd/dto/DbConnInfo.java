package com.migd.dto;

public record DbConnInfo(String host, int port, String db, String user, String password) {
}
