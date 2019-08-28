package com.raoulvdberge.raptor.model;

import java.util.Objects;

public class Route {
    private final int id;
    private final String name;

    public Route(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Route route = (Route) o;
        return id == route.id &&
            name.equals(route.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}