# 🧪 katalan Runner

**Unofficial Katalon Test Runner** - Execute Katalon automation scripts independently without Katalon Studio.

## ✨ Features

- ✅ Execute Katalon Test Cases (Groovy scripts)
- ✅ Execute Katalon Test Suites
- ✅ WebUI Keywords compatible with Katalon
- ✅ Mobile Keywords compatible with Katalon (Android/iOS via Appium)
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
cd katalan
mvn clean package
```

This creates `katalan-runner-1.0.0.jar` in the `target` folder.

## 🚀 Usage

### Run Test Suite

```bash
java -jar katalan-runner-1.0.0.jar run \
  -p /path/to/katalon/project \
  -ts "Test Suites/MySuite" \
  --platform chrome \
  --headless
```

### Run Test Case

```bash
java -jar katalan-runner-1.0.0.jar run \
  -tc /path/to/TestCase.groovy \
  --platform chrome
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
| `--platform` | What to run against: chrome, firefox, edge, safari, android, ios | chrome |
| `--headless` | Run in headless mode | false |
| `-r, --report` | Report output folder | reports |
| `--screenshot-on-failure` | Capture on failure | true |
| `--screenshot-on-success` | Capture on success | false |
| `--retry` | Retry count for failed tests | 0 |
| `--fail-fast` | Stop on first failure | false |
| `--timeout` | Implicit wait timeout (seconds) | 30 |
| `--profile` | Execution profile name | default |
| `--driver` | Custom WebDriver executable path | - |
| `--remote-url` | Remote WebDriver URL | - |
| `--browser-path` | Custom browser binary path | - |
| `--device-id, --udid` | Mobile device UDID/serial (auto-detected via `adb devices` if omitted and exactly one is connected) | - |
| `--platform-version` | Mobile OS version | - |
| `--app` | Path to `.apk`/`.ipa` to install and launch | - |
| `--app-package` | Package/bundle id of an already-installed app | - |
| `--app-activity` | Android activity to launch (with `--app-package`) | - |
| `--automation-name` | Appium engine (default `UiAutomator2`/`XCUITest`) | - |
| `--appium-url` | Use an already-running Appium server instead of auto-starting one | - |
| `--appium-port` | Port for the auto-started local Appium server (0 = any free port) | 0 |
| `--reset` / `--full-reset` | Reset app state between Mobile sessions | false |
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

### Using Execution Profiles

Execution profiles allow you to define different sets of GlobalVariables for different environments (e.g., dev, staging, production).

**Profile file location:** `Profiles/<profile_name>.glbl`

```bash
# Run with staging profile
java -jar katalan-runner-1.0.0.jar run \
  -p /path/to/project \
  -ts "Test Suites/MySuite" \
  --profile staging

# Run with production profile
java -jar katalan-runner-1.0.0.jar run \
  -p /path/to/project \
  -ts "Test Suites/MySuite" \
  --profile production
```

**Example profile structure:**
```
MyProject/
├── Profiles/
│   ├── default.glbl      # Default profile
│   ├── staging.glbl      # Staging environment
│   └── production.glbl   # Production environment
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

## 📱 Mobile Keywords (Appium)

katalan drives mobile apps through a local or remote [Appium](https://appium.io/) server using
the official `io.appium:java-client`. By default it auto-starts/stops a local Appium server for
you (just like it auto-manages chromedriver) - install Appium once (`npm i -g appium` plus the
driver for your platform, e.g. `appium driver install uiautomator2`) and katalan handles the rest.

> Pass `--platform android` (or `ios`) to select a mobile run - `--platform` is the single switch
> for what this run targets (chrome/firefox/edge/safari for Web, android/ios for Mobile). It only
> matters once your script actually calls into the matching keyword namespace (`WebUI.*` vs
> `Mobile.*`); katalan doesn't inspect the script upfront, so a script that never calls `Mobile.*`
> simply won't start an Appium session even if `--platform android` was passed, and vice versa.

```bash
# Auto-detects the single attached ADB device, installs and launches the app
java -jar katalan-runner.jar run \
  -p /path/to/project \
  -ts "Test Suites/Android" \
  --platform android \
  --app Documents/apk/myapp.apk

# Target an already-installed app on a specific device, against an Appium server you run yourself
java -jar katalan-runner.jar run \
  -p /path/to/project \
  -tc "Test Cases/Login/TC01.groovy" \
  --platform android \
  --device-id RR8W106N32M \
  --app-package com.example.app \
  --appium-url http://127.0.0.1:4723
```

### Application Management
- `startApplication(appFile, isRestartApp)` - Install and launch an app
- `startExistingApplication(appId)` - Launch/activate an already-installed app
- `closeApplication()` - End the app/Appium session
- `installApp(appFile)` / `removeApp(appId)` / `isAppInstalled(appId)` - App lifecycle
- `resetApp()` / `backgroundApp(seconds)` - Reset or background the app under test
- `getCurrentActivity()` / `getCurrentPackage()` - Android app introspection

### Device Info
- `getDeviceId()` / `getDeviceName()` / `getDeviceManufacturer()` / `getDeviceModel()`
- `getDeviceOS()` / `getDeviceOSVersion()`
- `getDeviceOrientation()` / `setDeviceOrientation(orientation)`

### Element Interaction
- `tap(testObject, timeout)` / `doubleTap(...)` / `longPress(testObject, duration, timeout)`
- `tapAtPosition(x, y)` - Tap raw screen coordinates
- `setText(testObject, text, timeout)` / `clearText(...)` / `getText(...)` / `getAttribute(...)`
- `swipe(startX, startY, endX, endY[, durationMs])`
- `scrollToText(text)` / `scrollToElement(testObject, timeout)`
- `getElementWidth/Height/TopPosition/LeftPosition(testObject, timeout)`

### Verification
- `verifyElementExist/NotExist(testObject, timeout)`
- `verifyElementVisible/NotVisible(testObject[, timeout])`
- `verifyElementText/ContainsText(testObject, text[, timeout])`
- `verifyElementAttributeValue(testObject, attribute, value, timeout)`
- `verifyElementChecked/NotChecked(testObject, timeout)`
- `verifyEqual(actual, expected)` / `verifyMatch(actual, expected, isRegex)`

### Navigation, Notifications & Network
- `pressBack()` / `pressHome()` / `hideKeyboard()` / `sendKeyEvent(keyCode)`
- `openNotifications()` / `closeNotifications()`
- `toggleWifi(state)` / `toggleData(state)` - accepts `"on"/"off"` or boolean
- `lockDevice()` / `unlockDevice()` / `isDeviceLocked()`

### Screenshot & Wait
- `takeScreenshot()` / `takeScreenshot(fileName)`
- `waitForElementPresent/NotPresent/Visible/NotVisible(testObject, timeout)`
- `delay(seconds)`

All keywords support the usual Katalon `FailureHandling` overload
(`STOP_ON_FAILURE` / `CONTINUE_ON_FAILURE` / `OPTIONAL`).

> **iOS note:** the iOS/XCUITest path is implemented for parity but has not been exercised against
> a real device/simulator in this environment - Android via UiAutomator2 is the validated path.

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

### Using Custom Browser Binary

```bash
java -jar katalan-runner.jar run \
  -tc MyTest.groovy \
  --browser-path /path/to/chrome \
  --platform chrome
```

### Using with Selenium Grid

```bash
java -jar katalan-runner.jar run \
  -tc MyTest.groovy \
  --remote-url http://localhost:4444/wd/hub \
  --platform chrome
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

**Disclaimer:** katalan is an unofficial project and is not affiliated with or endorsed by Katalon LLC.
