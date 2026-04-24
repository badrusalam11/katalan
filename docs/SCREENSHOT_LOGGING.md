# Screenshot Logging to execution0.log

## Overview
Katalan engine sekarang secara otomatis mencatat screenshot failure/error ke `execution0.log` untuk tracking di custom report.

## Implementation

### Location
File: `src/main/java/com/katalan/core/engine/KatalanEngine.java`

### Key Changes

1. **Method `takeScreenshot()` (Line ~577)**
   - Memanggil `logScreenshotCapture()` setiap kali screenshot diambil
   - Mengirim metadata screenshot ke execution0.log

2. **New Method `logScreenshotCapture()` (Line ~635)**
   - Log ke XmlKeywordLogger dengan level INFO
   - Menyimpan metadata: filename, type, path, attachment

## execution0.log Format

Ketika test case gagal dan screenshot diambil, entry berikut akan ditambahkan ke `execution0.log`:

```xml
<record>
  <date>2026-04-25T10:30:45</date>
  <millis>1714034445123</millis>
  <nanos>456000000</nanos>
  <sequence>123</sequence>
  <level>INFO</level>
  <class>com.katalan.core.engine.KatalanEngine</class>
  <method>takeScreenshot</method>
  <thread>1</thread>
  <message>Screenshot captured on error: BRICAMS_Transfer_Fund_BRI_to_BRI_Standart_TC_TC01_..._error_1777050048265.png</message>
  <properties>
    <screenshot.filename>BRICAMS_Transfer_Fund_BRI_to_BRI_Standart_TC_TC01_..._error_1777050048265.png</screenshot.filename>
    <screenshot.type>error</screenshot.type>
    <screenshot.path>/path/to/Reports/20260424_235540/.../screenshots/BRICAMS_..._error_1777050048265.png</screenshot.path>
    <attachment>BRICAMS_Transfer_Fund_BRI_to_BRI_Standart_TC_TC01_..._error_1777050048265.png</attachment>
    <testops-method-name>com.katalan.core.engine.KatalanEngine.takeScreenshot</testops-method-name>
    <testops-execution-stacktrace></testops-execution-stacktrace>
  </properties>
</record>
```

## Properties Captured

| Property | Description | Example |
|----------|-------------|---------|
| `screenshot.filename` | Nama file screenshot | `Test_Case_error_1777050048265.png` |
| `screenshot.type` | Tipe capture | `error` atau `failure` |
| `screenshot.path` | Full path ke file screenshot | `/Users/.../Reports/.../screenshots/test_error_123.png` |
| `attachment` | Katalon-compatible property | Same as filename |
| `testops-method-name` | Method yang capture screenshot | `com.katalan.core.engine.KatalanEngine.takeScreenshot` |

## Custom Report Integration

### Parsing Screenshot dari execution0.log

Contoh parsing di Python (CustomReport.py):

```python
import xml.etree.ElementTree as ET

def parse_screenshots(execution_log_path):
    tree = ET.parse(execution_log_path)
    root = tree.getroot()
    
    screenshots = []
    for record in root.findall('.//record'):
        level = record.find('level').text
        message = record.find('message').text
        
        # Check if this is a screenshot record
        if 'Screenshot captured' in message:
            props = record.find('properties')
            screenshot_info = {
                'filename': props.find('screenshot.filename').text,
                'type': props.find('screenshot.type').text,
                'path': props.find('screenshot.path').text,
                'timestamp': record.find('date').text
            }
            screenshots.append(screenshot_info)
    
    return screenshots
```

### Groovy Parsing (untuk Listener):

```groovy
import groovy.xml.XmlSlurper

def parseScreenshots(executionLogPath) {
    def xml = new XmlSlurper().parse(new File(executionLogPath))
    def screenshots = []
    
    xml.record.each { record ->
        if (record.message.text().contains('Screenshot captured')) {
            def props = record.properties
            screenshots << [
                filename: props.'screenshot.filename'.text(),
                type: props.'screenshot.type'.text(),
                path: props.'screenshot.path'.text(),
                timestamp: record.date.text()
            ]
        }
    }
    
    return screenshots
}
```

## Benefits

✅ **Trackable**: Screenshot tercatat di execution0.log dengan timestamp  
✅ **Custom Report Ready**: Bisa di-parse untuk custom report generation  
✅ **Metadata Rich**: Menyimpan filename, type, dan full path  
✅ **Katalon Compatible**: Menggunakan property `attachment` seperti Katalon Studio  
✅ **Automatic**: Tidak perlu kode tambahan di test script  

## Use Cases

1. **Custom HTML Report**: Menampilkan screenshot di report dengan link/preview
2. **PDF Report**: Embed screenshot image ke PDF
3. **Confluence Report**: Upload screenshot dan link ke report
4. **Email Notification**: Attach screenshot file berdasarkan path dari log
5. **Test Analytics**: Track berapa banyak test yang capture screenshot

## Example: Custom Report Usage

```python
# CustomReport.py
screenshots = parse_screenshots('Reports/20260424_235540/execution0.log')

for ss in screenshots:
    print(f"Test Failed at {ss['timestamp']}")
    print(f"Screenshot: {ss['filename']}")
    print(f"Type: {ss['type']}")
    print(f"Path: {ss['path']}")
    
    # Copy to report directory or embed in HTML
    shutil.copy(ss['path'], f"report_output/{ss['filename']}")
```

## Notes

- Screenshot hanya dilog jika `--screenshot-on-failure=true` (default)
- Untuk screenshot manual via `WebUI.takeScreenshot()`, sudah ada logging terpisah
- Property `screenshot.type` bisa bernilai `"failure"` atau `"error"`
- Entry log menggunakan level `INFO` untuk tidak menganggu flow test

## Testing

Build dan test:
```bash
cd /Users/computer/Documents/repository/katalan
./katalan.sh --test-suite "Test Suites/Example" --screenshot-on-failure=true
```

Check execution0.log:
```bash
grep "Screenshot captured" Reports/*/execution0.log
```
