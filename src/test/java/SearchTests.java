import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

public class SearchTests extends TestBase {
    @Test
    void successfulSearchTest() {
        open("/search");
        $("[aria-label='Search GitHub']").setValue("qa.guru").pressEnter();
        $("[data-testid='results-list']").shouldHave(text("QA.GURU"));
    }

    @Test
    void noResultsForImpossibleStarsRangeTest() {
        open("/search");
        $("[aria-label='Search GitHub']").setValue("stars:<0").pressEnter();
        $("main").shouldHave(text("0 results"), text("Your search did not match any repositories"));
        $("[data-testid='results-list']").shouldNot(exist);
    }

    @Test
    void noResultsForHugeStarsCountTest() {
        open("/search");
        $("[aria-label='Search GitHub']").setValue("stars:>999999999").pressEnter();
        $("main").shouldHave(text("0 results"), text("Your search did not match any repositories"));
        $("[data-testid='results-list']").shouldNot(exist);
    }
}
