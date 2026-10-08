package com.trackflow.tms.util;

import com.trackflow.tms.exception.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Rejects sort properties that are not on an allow-list, so a bad ?sort=
 * becomes a 400 instead of a query error (and cannot probe unexposed fields).
 */
public final class SortGuard {

    private SortGuard() {
    }

    public static Pageable require(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'. Allowed: "
                        + String.join(", ", allowed.stream().sorted().toList()));
            }
        }
        return pageable;
    }
}
