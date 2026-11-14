package com.baracel.portal.access.service;

/** Second-factor state of a sign-in method. Social providers decide this themselves. */
public enum MfaState { ENABLED, DISABLED, PROVIDER_MANAGED }
