package com.hansung.hsp.space;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class SpaceSpecifications {
    private SpaceSpecifications() {}

    public static Specification<Space> filter(String venue, String type, Boolean bookingEnabled,
            Integer minCapacity, String query) {
        return (root, criteria, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (venue != null) predicates.add(cb.equal(root.get("venue"), venue));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (bookingEnabled != null) predicates.add(cb.equal(root.get("bookingEnabled"), bookingEnabled));
            // minCapacity means a space capable of accommodating at least this many people.
            // Unknown maximum capacities cannot guarantee that and are excluded from this filter.
            if (minCapacity != null) predicates.add(cb.ge(root.get("maxCapacity"), minCapacity));
            if (query != null && !query.isBlank()) {
                String escaped = query.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("location")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}

