package com.qic.library.tests;

import com.qic.library.tests.framework.client.InventoryTracker;
import com.qic.library.tests.framework.client.LibraryClient;
import com.qic.library.tests.framework.fixtures.BookFixtures;
import com.qic.library.tests.framework.support.ServiceHealth;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;

/**
 * Base class for API tests. JUnit creates a new instance per test method, so each test gets its own
 * client and tracker, which keeps parallel tests independent. Copies a test borrowed and didn't
 * return are returned after it, leaving the service as the test found it.
 */
@Epic("Library API")
public abstract class BaseApiTest {

    protected final InventoryTracker inventory = new InventoryTracker();
    protected final LibraryClient library = new LibraryClient(inventory);
    protected final BookFixtures books = new BookFixtures(library);

    @BeforeAll
    static void checkServiceIsAvailable() {
        ServiceHealth.ensureAvailable();
    }

    @AfterEach
    void returnBorrowedCopies() {
        Map<Long, Integer> outstanding = inventory.outstanding();
        if (outstanding.isEmpty()) {
            return;
        }
        Allure.step("Clean-up: return copies borrowed by the test " + outstanding, () ->
                outstanding.forEach((bookId, copies) -> {
                    for (int i = 0; i < copies; i++) {
                        library.returnBook(bookId);
                    }
                }));
    }
}
