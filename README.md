# 🧪 Katalan Runner

**Unofficial Katalon Test Runner** - Execute Katalon automation scripts independently without Katalon Studio.

## ✨ Features

- ✅ Execute Katalon Test Cases (Groovy scripts)
- ✅ Execute Katalon Test Suites
- ✅ WebUI Keywords compatible with Katalon
- ✅ Object Repository support
- ✅ GlobalVariable support
- ✅ Beautiful HTML reports
- ✅ Screenshot on failure/success
- ✅ Retry failed tests
- ✅ Headless browser mode
- ✅ Multiple browser support (Chrome, Firefox, Edge, Safari)
- ✅ Remote WebDriver support (Selenium Grid)

## 📦 Installation

### Prerequisites

- Java 11 or higher
- Maven 3.6+

### Build from Source

```bash
cd Katalan
mvn clean package
```

This creates `katalan-runner-1.0.0.jar` in the `target` folder.

## 🚀 Usage

### Run Test Suite

```bash
java -jar katalan-runner-1.0.0.jar run \
  -p /path/to/katalon/project \
  -ts "Test Suites/MySuite" \
  -b chrome \
  --headless
```

### Run Test Case

```bash
java -jar katalan-runner-1.0.0.jar run \
  -tc /path/to/TestCase.groovy \
  -b chrome
```

### Run Multiple Test Cases

```bash
java -jar katalan-runner-1.0.0.jar run \
  -tc /path/to/Test1.groovy \
  -tc /path/to/Test2.groovy \
  -r reports
```

### CLI Options

| Option | Description | Default |
|--------|-------------|---------|
| `-p, --project` | Katalon project folder path | - |
| `-ts, --test-suite` | Test suite to run | - |
| `-tc, --test-case` | Test case file(s) to run | - |
| `-b, --browser` | Browser: chrome, firefox, edge, safari | chrome |
| `--headless` | Run in headless mode | false |
| `-r, --report` | Report output folder | reports |
| `--screenshot-on-failure` | Capture on failure | true |
| `--screenshot-on-success` | Capture on success | false |
| `--retry` | Retry count for failed tests | 0 |
| `--fail-fast` | Stop on first failure | false |
| `--timeout` | Implicit wait timeout (seconds) | 30 |
| `--remote-url` | Remote WebDriver URL | - |
| `-v, --verbose` | Verbose logging | false |

## 📝 Writing Test Scripts

### Basic Test Case

```groovy
// TestCase.groovy
import com.katalan.keywords.WebUI
import com.katalan.core.model.TestObject

// Open browser
WebUI.openBrowser("https://example.com")

// Find elements using TestObject
def loginButton = TestObject.xpath("Login Button", "//button[@id='login']")
def usernameInput = TestObject.id("Username Input", "username")
def passwordInput = TestObject.id("Password Input", "password")

// Interact with elements
WebUI.setText(usernameInput, "admin")
WebUI.setText(passwordInput, "password123")
WebUI.click(loginButton)

// Verify
WebUI.verifyElementPresent(findTestObject('Page/Dashboard'), 10)

// Close browser
WebUI.closeBrowser()
```

### Using GlobalVariable

```groovy
import com.katalan.keywords.GlobalVariable

// Set global variables
GlobalVariable.set("baseUrl", "https://example.com")
GlobalVariable.set("username", "admin")

// Use global variables
WebUI.openBrowser(GlobalVariable.get("baseUrl"))
WebUI.setText(findTestObject('Page/Username'), GlobalVariable.get("username"))
```

### Using KeywordUtil

```groovy
import com.katalan.keywords.KeywordUtil

// Logging
KeywordUtil.logInfo("Starting test")
KeywordUtil.logWarning("This is a warning")

// Mark step results
KeywordUtil.markPassed("Login successful")
KeywordUtil.markFailed("Element not found") // Throws StepFailedException
```

## 📊 WebUI Keywords

### Browser Operations
- `openBrowser(url)` - Open browser and navigate
- `navigateToUrl(url)` - Navigate to URL
- `closeBrowser()` - Close browser
- `refresh()` - Refresh page
- `back()` / `forward()` - Navigate history
- `maximizeWindow()` - Maximize window
- `getUrl()` / `getWindowTitle()` - Get current URL/title

### Element Interactions
- `click(testObject)` - Click element
- `doubleClick(testObject)` - Double click
- `rightClick(testObject)` - Right click (context menu)
- `setText(testObject, text)` - Set text input
- `clearText(testObject)` - Clear text
- `getText(testObject)` - Get element text
- `getAttribute(testObject, attr)` - Get attribute
- `sendKeys(testObject, keys...)` - Send keyboard keys

### Wait Operations
- `waitForElementPresent(testObject, timeout)` - Wait for element
- `waitForElementVisible(testObject, timeout)` - Wait for visibility
- `waitForElementClickable(testObject, timeout)` - Wait for clickable
- `waitForPageLoad(timeout)` - Wait for page load
- `delay(seconds)` - Simple delay

### Verification
- `verifyElementPresent(testObject, timeout)` - Verify present
- `verifyElementVisible(testObject, timeout)` - Verify visible
- `verifyElementText(testObject, text)` - Verify text
- `verifyElementChecked(testObject, timeout)` - Verify checked

### Select/Dropdown
- `selectOptionByLabel(testObject, label)` - Select by text
- `selectOptionByValue(testObject, value)` - Select by value
- `selectOptionByIndex(testObject, index)` - Select by index

### Frame & Window
- `switchToFrame(index/name/testObject)` - Switch to frame
- `switchToDefaultContent()` - Switch to main content
- `switchToWindowTitle(title)` - Switch window by title
- `switchToWindowIndex(index)` - Switch window by index

### Alert
- `acceptAlert()` - Accept alert
- `dismissAlert()` - Dismiss alert
- `getAlertText()` - Get alert text
- `setAlertText(text)` - Set alert text

### Screenshot
- `takeScreenshot()` - Capture screenshot
- `takeScreenshot(filename)` - Capture with name
- `takeElementScreenshot(testObject, filename)` - Capture element

### JavaScript
- `executeJavaScript(script, args...)` - Execute JS
- `scrollToElement(testObject)` - Scroll to element
- `scrollToPosition(x, y)` - Scroll to position
- `scrollToTop()` / `scrollToBottom()` - Scroll page

## 🗂️ Project Structure

### Katalon Project Structure (Supported)

```
MyKatalonProject/
├── Object Repository/
│   └── Page/
│       └── element.rs
├── Test Cases/
│   └── MyTestCase.tc
├── Test Suites/
│   └── MySuite.ts
└── Scripts/
    └── MyTestCase/
        └── Script123456.groovy
```

### Standalone Test Scripts

You can also run standalone Groovy scripts without a Katalon project structure:

```bash
java -jar katalan-runner.jar run -tc MyTest.groovy
```

## 📈 Reports

HTML reports are generated automatically after execution:

```
reports/
├── index.html          # Main summary
├── suite_name.html     # Suite details
├── style.css           # Styles
└── screenshots/        # Failure screenshots
```

## 🔧 Configuration

### Using with Selenium Grid

```bash
java -jar katalan-runner.jar run \
  -tc MyTest.groovy \
  --remote-url http://localhost:4444/wd/hub \
  -b chrome
```

### Headless Mode with Retry

```bash
java -jar katalan-runner.jar run \
  -p /project \
  -ts "Test Suites/Regression" \
  --headless \
  --retry 2 \
  --fail-fast
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## 📄 License

MIT License - Use freely for any purpose.

---

**Disclaimer:** Katalan is an unofficial project and is not affiliated with or endorsed by Katalon LLC.
