package tests;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.logevents.SelenideLogger;
import io.qameta.allure.selenide.AllureSelenide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static com.codeborne.selenide.Selenide.closeWebDriver;

public class TestBase {
    @BeforeAll
    static void setUpAllure() {
        SelenideLogger.addListener("allure", new AllureSelenide());
    }

    @BeforeEach
    void setUp() {
        Configuration.baseUrl = System.getProperty("baseUrl", "https://github.com");
        Configuration.browser = System.getProperty("browser", "chrome");
        Configuration.browserSize = System.getProperty("browserSize", "1920x1080");
        Configuration.headless = Boolean.parseBoolean(System.getProperty("headless", "false"));

        String remote = System.getProperty("remote", "");
        if (!remote.isEmpty()) {
            Configuration.remote = remote;
        }

        String browserVersion = System.getProperty("browserVersion", "");
        if (!browserVersion.isEmpty()) {
            Configuration.browserVersion = browserVersion;
        }
    }

    @AfterEach
    void tearDown() {
        closeWebDriver();
    }
}
