package sn.niirma.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sn.niirma.shared.persistence.UuidV7;

class UuidV7Test {

    @Test
    void generatesVersion7Rfc9562Uuids() {
        UUID uuid = UuidV7.generate();

        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheCreationTimestamp() {
        long before = System.currentTimeMillis();
        UUID uuid = UuidV7.generate();
        long after = System.currentTimeMillis();

        long embeddedMillis = uuid.getMostSignificantBits() >>> 16;
        assertThat(embeddedMillis).isBetween(before, after);
    }

    @Test
    void uuidsGeneratedInDifferentMillisecondsSortChronologically() throws InterruptedException {
        List<UUID> generated = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            generated.add(UuidV7.generate());
            Thread.sleep(2);
        }

        assertThat(generated).isSortedAccordingTo(
                (a, b) -> Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits()));
    }
}
