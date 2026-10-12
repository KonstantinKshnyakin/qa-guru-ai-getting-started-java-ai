package pages;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.$;

public class SearchResultsPage {
    private final SelenideElement resultsList = $("[data-testid='results-list']");
    private final SelenideElement main = $("main");

    public SearchResultsPage shouldHaveResult(String expectedText) {
        resultsList.shouldHave(text(expectedText));
        return this;
    }

    public SearchResultsPage shouldBeEmpty() {
        main.shouldHave(text("0 results"), text("Your search did not match any repositories"));
        resultsList.shouldNot(exist);
        return this;
    }
}
