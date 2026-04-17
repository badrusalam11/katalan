/**
 * Example Test Case - Google Search
 * 
 * This script demonstrates basic katalan WebUI keywords
 * Run with: java -jar katalan-runner.jar run -tc examples/GoogleSearchTest.groovy
 */

import com.katalan.keywords.WebUI
import com.katalan.keywords.KeywordUtil
import com.katalan.core.model.TestObject

// Log start
KeywordUtil.logInfo("Starting Google Search Test")

// Define test objects using XPath
def searchInput = TestObject.xpath("Search Input", "//textarea[@name='q']")
def searchButton = TestObject.xpath("Search Button", "(//input[@name='btnK'])[2]")

try {
    // Open Google
    WebUI.openBrowser("https://www.google.com")
    KeywordUtil.logInfo("Opened Google homepage")
    
    // Wait for page to load
    WebUI.waitForPageLoad(10)
    
    // Verify search input is present
    if (WebUI.verifyElementPresent(searchInput, 10)) {
        KeywordUtil.markPassed("Search input is present")
    }
    
    // Enter search query
    WebUI.setText(searchInput, "katalan Test Runner")
    KeywordUtil.logInfo("Entered search query")
    
    // Submit search (press Enter)
    WebUI.sendKeys(searchInput, org.openqa.selenium.Keys.ENTER)
    
    // Wait for results
    WebUI.delay(2)
    
    // Verify we're on search results page
    String currentUrl = WebUI.getUrl()
    if (currentUrl.contains("search")) {
        KeywordUtil.markPassed("Search results page loaded successfully")
    }
    
    // Get page title
    String title = WebUI.getWindowTitle()
    KeywordUtil.logInfo("Page title: " + title)
    
    // Take screenshot
    WebUI.takeScreenshot("google_search_results")
    
    KeywordUtil.markPassed("Google Search Test completed successfully!")
    
} catch (Exception e) {
    KeywordUtil.logError("Test failed: " + e.message)
    WebUI.takeScreenshot("error_screenshot")
    throw e
    
} finally {
    // Close browser
    WebUI.closeBrowser()
    KeywordUtil.logInfo("Browser closed")
}
