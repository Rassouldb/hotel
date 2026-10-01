package sn.niirma.shared.api;

import org.springframework.data.domain.Page;

/**
 * Métadonnées de pagination renvoyées dans {@code meta} (pages numérotées à partir de 1).
 */
public record PageMeta(int page, int size, long total) {

    public static PageMeta from(Page<?> page) {
        return new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }
}
