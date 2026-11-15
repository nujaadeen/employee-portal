package com.baracel.portal.ai.social;

import static org.junit.jupiter.api.Assertions.*;

import com.baracel.portal.ai.client.SimpleCircuitBreaker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SocialUnitTest {
    static String key(int seed) {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, (byte) seed);
        return Base64.getEncoder().encodeToString(k);
    }

    static TokenCipher cipher(String id, String key, String old) {
        return new TokenCipher(new SocialProperties(null, new SocialProperties.Crypto(key, id, old), "http://x"));
    }

    @Test
    void tokensRoundTripAndEveryEncryptionIsDifferent() {
        TokenCipher c = cipher("k1", key(1), "");
        String a = c.encrypt("secret-token"), b = c.encrypt("secret-token");
        assertNotEquals(a, b, "a fresh random IV each time");
        assertFalse(a.contains("secret-token"));
        assertTrue(a.startsWith("k1:"));
        assertEquals("secret-token", c.decrypt(a));
    }

    @Test
    void aRotatedKeyStillReadsOldValuesAndNewValuesUseTheNewKey() {
        String oldValue = cipher("k1", key(1), "").encrypt("old-token");
        TokenCipher rotated = cipher("k2", key(2), "k1:" + key(1));
        assertEquals("old-token", rotated.decrypt(oldValue));
        assertTrue(rotated.encrypt("x").startsWith("k2:"));
        assertThrows(IllegalStateException.class, () -> cipher("k2", key(2), "").decrypt(oldValue), "without the old key it cannot be read");
    }

    @Test
    void tamperedOrWrongKeyValuesAreRejected() {
        TokenCipher c = cipher("k1", key(1), "");
        String enc = c.encrypt("token");
        String tampered = enc.substring(0, enc.length() - 2) + (enc.endsWith("AA") ? "BB" : "AA");
        assertThrows(IllegalStateException.class, () -> c.decrypt(tampered));
        assertThrows(IllegalStateException.class, () -> cipher("k1", key(9), "").decrypt(enc));
        assertThrows(IllegalStateException.class, () -> c.decrypt("garbage"));
        assertFalse(cipher("k1", "", "").configured());
        assertThrows(IllegalStateException.class, () -> cipher("k1", "", "").encrypt("x"));
        assertThrows(IllegalStateException.class, () -> cipher("k1", Base64.getEncoder().encodeToString(new byte[16]), ""));   // not 32 bytes
    }

    @Test
    void commentaryEscapesLittleTextCharactersAndWritesHashtagsAsWords() {
        String c = PublishService.commentary("Scan. Pay. Done.", "Pay (fast) @ the counter #1 *now* _today_ {x} [y] a|b ~c <d> \\e", "#Baracel #QR_Payments #Small-Business");
        assertEquals("Scan. Pay. Done.\n\nPay \\(fast\\) \\@ the counter \\#1 \\*now\\* \\_today\\_ \\{x\\} \\[y\\] a\\|b \\~c \\<d\\> \\\\e\n\n#Baracel #QRPayments #SmallBusiness", c);
        assertEquals("Only a caption", PublishService.commentary("", "Only a caption", ""));
        assertEquals("H\n\nC", PublishService.commentary(" H ", " C ", null));
    }

    @Test
    void theBreakerOpensAfterTheThresholdTriesOnceWhenHalfOpenAndClosesOnSuccess() {
        class Ticking extends Clock {
            Instant now = Instant.parse("2026-11-15T10:00:00Z");
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return now; }
        }
        Ticking clock = new Ticking();
        SimpleCircuitBreaker b = new SimpleCircuitBreaker(3, Duration.ofSeconds(30), clock);
        assertTrue(b.tryAcquire());
        b.failure(); b.failure();
        assertEquals(SimpleCircuitBreaker.State.CLOSED, b.state());
        b.success();                                   // a success resets the count
        b.failure(); b.failure();
        assertEquals(SimpleCircuitBreaker.State.CLOSED, b.state());
        b.failure();
        assertEquals(SimpleCircuitBreaker.State.OPEN, b.state());
        assertFalse(b.tryAcquire());
        clock.now = clock.now.plusSeconds(31);
        assertEquals(SimpleCircuitBreaker.State.HALF_OPEN, b.state());
        assertTrue(b.tryAcquire(), "one trial call");
        assertFalse(b.tryAcquire(), "but only one");
        b.failure();                                   // the trial failed: open again for another period
        assertEquals(SimpleCircuitBreaker.State.OPEN, b.state());
        clock.now = clock.now.plusSeconds(31);
        assertTrue(b.tryAcquire());
        b.success();
        assertEquals(SimpleCircuitBreaker.State.CLOSED, b.state());
        assertTrue(b.tryAcquire());
    }
}
