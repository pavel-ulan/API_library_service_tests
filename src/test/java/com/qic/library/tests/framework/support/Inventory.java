package com.qic.library.tests.framework.support;

/**
 * Name of the JUnit resource lock that protects book copy counters during parallel runs.
 * <ul>
 *   <li>Tests that borrow or return copies: {@code @ResourceLock(Inventory.LOCK)} (read-write).</li>
 *   <li>Tests that compare counters across several requests:
 *       {@code @ResourceLock(value = Inventory.LOCK, mode = ResourceAccessMode.READ)}.</li>
 *   <li>Everything else (static data, validation, 404s) runs without a lock.</li>
 * </ul>
 */
public final class Inventory {

    public static final String LOCK = "library-inventory";

    private Inventory() {
    }
}
