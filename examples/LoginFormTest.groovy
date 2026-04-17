/**
 * Example Test Case - Login Form Test
 * 
 * This script demonstrates form interaction with katalan
 * Uses a demo login page for testing
 */

import com.katalan.keywords.WebUI
import com.katalan.keywords.KeywordUtil
import com.katalan.keywords.GlobalVariable
import com.katalan.core.model.TestObject

// Set global variables for test data
GlobalVariable.set("baseUrl", "https://the-internet.herokuapp.com")
GlobalVariable.set("username", "tomsmith")
GlobalVariable.set("password", "SuperSecretPassword!")

// Define test objects
def usernameInput = TestObject.id("Username Field", "username")
def passwordInput = TestObject.id("Password Field", "password")
def loginButton = TestObject.css("Login Button", "button[type='submit']")
def flashMessage = TestObject.id("Flash Message", "flash")
def logoutButton = TestObject.css("Logout Button", "a[href='/logout']")

KeywordUtil.logInfo("=== Login Form Test ===")

try {
    // Step 1: Open login page
    KeywordUtil.logInfo("Step 1: Opening login page")
    WebUI.openBrowser(GlobalVariable.get("baseUrl") + "/login")
    WebUI.waitForPageLoad(10)
    
    // Verify page loaded
    String pageTitle = WebUI.getWindowTitle()
    assert pageTitle.contains("Internet") : "Page title should contain 'Internet'"
    KeywordUtil.markPassed("Login page opened: " + pageTitle)
    
    // Step 2: Enter credentials
    KeywordUtil.logInfo("Step 2: Entering credentials")
    WebUI.waitForElementVisible(usernameInput, 10)
    WebUI.setText(usernameInput, GlobalVariable.get("username"))
    WebUI.setText(passwordInput, GlobalVariable.get("password"))
    KeywordUtil.markPassed("Credentials entered")
    
    // Step 3: Click login button
    KeywordUtil.logInfo("Step 3: Clicking login button")
    WebUI.click(loginButton)
    WebUI.waitForPageLoad(10)
    
    // Step 4: Verify successful login
    KeywordUtil.logInfo("Step 4: Verifying login success")
    WebUI.waitForElementVisible(flashMessage, 10)
    String message = WebUI.getText(flashMessage)
    
    if (message.contains("You logged into a secure area")) {
        KeywordUtil.markPassed("Login successful!")
    } else {
        KeywordUtil.markFailed("Login failed - unexpected message: " + message)
    }
    
    // Verify logout button is present
    assert WebUI.verifyElementPresent(logoutButton, 5) : "Logout button should be present"
    
    // Take success screenshot
    WebUI.takeScreenshot("login_success")
    
    // Step 5: Logout
    KeywordUtil.logInfo("Step 5: Logging out")
    WebUI.click(logoutButton)
    WebUI.waitForPageLoad(10)
    
    // Verify back to login page
    WebUI.waitForElementVisible(usernameInput, 10)
    KeywordUtil.markPassed("Logout successful - back to login page")
    
    KeywordUtil.logInfo("=== Test Completed Successfully ===")
    
} catch (AssertionError e) {
    KeywordUtil.logError("Assertion failed: " + e.message)
    WebUI.takeScreenshot("assertion_failure")
    throw e
    
} catch (Exception e) {
    KeywordUtil.logError("Test error: " + e.message)
    WebUI.takeScreenshot("test_error")
    throw e
    
} finally {
    WebUI.closeBrowser()
}
