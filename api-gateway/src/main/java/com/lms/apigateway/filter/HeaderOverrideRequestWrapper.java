package com.lms.apigateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.*;

public class HeaderOverrideRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String> overrides = new HashMap<>();
    private final Set<String> removed = new HashSet<>();

    public HeaderOverrideRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    public void setHeader(String name, String value) {
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
        Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
        names.removeAll(removed);
        names.addAll(overrides.keySet());
        return Collections.enumeration(names);
    }
}