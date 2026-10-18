package com.lms.apigateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.*;

public class HeaderOverrideRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> overrides = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<String> removed = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public HeaderOverrideRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    public void setHeader(String name, String value) {
        Objects.requireNonNull(value, "Header value must not be null");
        overrides.put(name, value);
        removed.remove(name);
    }

    public void removeHeader(String name) {
        removed.add(name);
        overrides.remove(name);
    }

    @Override
    public String getHeader(String name) {
        if (overrides.containsKey(name)) return overrides.get(name);
        if (removed.contains(name)) return null;
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (overrides.containsKey(name)) {
            return Collections.enumeration(List.of(overrides.get(name)));
        }
        if (removed.contains(name)) {
            return Collections.emptyEnumeration();
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(Collections.list(super.getHeaderNames()));
        names.removeIf(removed::contains);
        names.addAll(overrides.keySet());
        return Collections.enumeration(names);
    }
}
