package com.baracel.portal.access.service;

import java.util.List;

/** The realm roles the portal knows. Only this module maps them to permissions. */
public final class PortalRoles {
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String MANAGER = "MANAGER";
    public static final String HR = "HR";
    public static final String SALES = "SALES";
    public static final String ENGINEER = "ENGINEER";
    public static final String ADMIN = "ADMIN";
    public static final List<String> ALL = List.of(EMPLOYEE, MANAGER, HR, SALES, ENGINEER, ADMIN);

    private PortalRoles() {}
}
