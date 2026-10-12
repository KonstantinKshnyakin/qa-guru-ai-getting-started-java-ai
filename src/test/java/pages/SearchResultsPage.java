package pages;

import com.codeborne.selenide.SelenideElement;
import io.qameta.allure.Step;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.$;

public class SearchResultsPage {
    private final SelenideElement resultsList = $("[data-testid='results-list']");
    private final SelenideElement main = $("main");

    @Step("Check results contain \"{0}\"")
    public SearchResultsPage shouldHaveResult(String expectedText) {
        resultsList.shouldHave(text(expectedText));
        return this;
    }

    @Step("Check empty search results state")
    public SearchResultsPage shouldBeEmpty() {
        main.shouldHave(text("0 results"), text("Your search did not match any repositories"));
        resultsList.shouldNot(exist);
        return this;
    }
}
