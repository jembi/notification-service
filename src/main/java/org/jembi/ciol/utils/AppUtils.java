package org.jembi.ciol.utils;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public class AppUtils implements Serializable {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Serial
    private static final long serialVersionUID = 1L;

    private AppUtils() {
        OBJECT_MAPPER.configOverride(String.class).setSetterInfo(JsonSetter.Value.forValueNulls((Nulls.SET)));
    }

    public static AppUtils getInstance() {
        return UtilsSingletonHolder.INSTANCE;
    }

    public static boolean isNullOrEmpty(final Collection<?> c) {
        return c == null || c.isEmpty();
    }

    public static boolean isNullOrEmpty(final Map<?, ?> m) {
        return m == null || m.isEmpty();
    }

    @Serial
    protected Object readResolve() {
        return getInstance();
    }

    private static class UtilsSingletonHolder {
        public static final AppUtils INSTANCE = new AppUtils();
    }
}