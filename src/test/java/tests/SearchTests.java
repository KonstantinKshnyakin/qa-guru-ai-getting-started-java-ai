package tests;

import org.junit.jupiter.api.Test;
import pages.SearchPage;

public class SearchTests extends TestBase {
    private final SearchPage searchPage = new SearchPage();

    @Test
    void successfulSearchTest() {
        searchPage.open()
                .searchFor("qa.guru")
                .shouldHaveResult("QA.GURU");
    }

    @Test
    void noResultsForImpossibleStarsRangeTest() {
        searchPage.open()
                .searchFor("stars:<0")
                .shouldBeEmpty();
    }

    @Test
    void noResultsForHugeStarsCountTest() {
        searchPage.open()
                .searchFor("stars:>999999999")
                .shouldBeEmpty();
    }
}
