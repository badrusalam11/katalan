package com.kms.katalon.core.driver;

/**
 * Katalon compatibility: common interface of Katalon's driver-type enums
 * (e.g. {@code WebUIDriverType}).
 */
public interface IDriverType {
    String getName();

    String getPropertyKey();

    String getPropertyValue();
}
