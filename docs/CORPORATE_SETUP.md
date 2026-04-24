# Katalan Corporate CI/CD Setup Guide

## Problem
Corporate networks biasanya memerlukan whitelist DNS untuk akses internet. WebDriverManager perlu download chromedriver dari internet.

## Solution Options

### Option 1: Pre-Download Driver (RECOMMENDED) ⭐

Download chromedriver sekali, simpan di repository atau artifact storage internal.

#### Step 1: Manual Download (Sekali saja)
```bash
# Download chromedriver versi tertentu
curl -O https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/131.0.6778.85/mac-x64/chromedriver-mac-x64.zip

# Atau gunakan WebDriverManager CLI
java -jar katalan.jar resolveDriverFor chrome
```

#### Step 2: Simpan di Project atau Artifact Storage
```
project/
  drivers/
    chromedriver_mac      # Mac version
    chromedriver.exe      # Windows version  
    chromedriver_linux    # Linux version
```

#### Step 3: Configure Katalan untuk pakai local driver
```groovy
// Di test script atau profile
import com.kms.katalon.core.configuration.RunConfiguration

// Set path ke local driver
RunConfiguration.setDriverPath("/path/to/drivers/chromedriver")
```

Atau via command line:
```bash
./katalan.sh -browserType=CHROME -driverPath=/path/to/drivers/chromedriver test.groovy
```

---

### Option 2: Internal Mirror/Artifact Repository

Setup internal repository (Nexus, Artifactory, dll) sebagai mirror.

#### Step 1: Whitelist DNS untuk Mirror
Minta IT team whitelist domain:
- `edgedl.me.gvt1.com` (Chrome for Testing)
- `chromedriver.storage.googleapis.com` (Legacy Chrome)
- `msedgedriver.azureedge.net` (Edge)

#### Step 2: Setup Mirror dengan WebDriverManager
```java
// Di Java code
WebDriverManager.chromedriver()
    .driverRepositoryUrl(new URL("https://your-internal-mirror.company.com/chromedriver/"))
    .setup();
```

Atau via properties:
```properties
# webdrivermanager.properties
wdm.chromeDriverUrl=https://your-internal-mirror.company.com/chromedriver/
```

Atau via environment variable:
```bash
export WDM_CHROMEDRIVERURL=https://your-internal-mirror.company.com/chromedriver/
```

---

### Option 3: Cache Driver di CI/CD Pipeline

Setup cache di pipeline agar download sekali saja.

#### GitLab CI Example:
```yaml
.test_template:
  cache:
    key: chrome-driver-cache
    paths:
      - ~/.cache/selenium/
  before_script:
    - mkdir -p ~/.cache/selenium
  script:
    - ./katalan.sh test.groovy
```

#### Jenkins Example:
```groovy
pipeline {
    agent any
    
    environment {
        WDM_CACHEPATH = "${WORKSPACE}/.selenium-cache"
    }
    
    stages {
        stage('Test') {
            steps {
                sh './katalan.sh test.groovy'
            }
        }
    }
}
```

---

### Option 4: Disable Auto-Download (Offline Mode)

Jika sudah pre-download, disable external connections completely:

```java
// Di code
WebDriverManager.chromedriver()
    .avoidExternalConnections()
    .cachePath("/path/to/cached/drivers")
    .setup();
```

Environment variable:
```bash
export WDM_AVOIDEXTERNALCONNECTIONS=true
export WDM_CACHEPATH=/path/to/cached/drivers
```

---

## Configuration Options untuk Corporate Environment

### Via System Properties:
```bash
java -Dwdm.cachePath=/custom/cache \
     -Dwdm.proxy=proxy.company.com:8080 \
     -Dwdm.proxyUser=username \
     -Dwdm.proxyPass=password \
     -jar katalan.jar test.groovy
```

### Via Environment Variables:
```bash
export WDM_CACHEPATH=/custom/cache
export WDM_PROXY=proxy.company.com:8080
export WDM_PROXYUSER=username
export WDM_PROXYPASS=password

# Atau gunakan HTTPS_PROXY
export HTTPS_PROXY=http://username:password@proxy.company.com:8080
```

### Via Properties File:
```properties
# src/main/resources/webdrivermanager.properties
wdm.cachePath=/custom/cache
wdm.proxy=proxy.company.com:8080
wdm.proxyUser=username
wdm.proxyPass=password
wdm.timeout=60
wdm.avoidExternalConnections=false
```

---

## DNS Whitelist Requirements

Minimal whitelist ini untuk IT team:

### Untuk Chrome:
- ✅ `edgedl.me.gvt1.com` (primary - Chrome for Testing)
- ✅ `chromedriver.storage.googleapis.com` (legacy - Chrome < 115)
- ✅ `googlechromelabs.github.io` (metadata)

### Untuk Firefox:
- ✅ `api.github.com` (geckodriver metadata)
- ✅ `github.com` (geckodriver download)

### Untuk Edge:
- ✅ `msedgedriver.azureedge.net`

### Optional (untuk online commands database):
- ✅ `raw.githubusercontent.com` (commands database)

---

## Best Practice untuk Corporate CI/CD

1. **Pre-download drivers** ke artifact repository internal
2. **Cache** folder `~/.cache/selenium/` di CI/CD pipeline
3. **Set timeout** lebih tinggi jika network lambat: `wdm.timeout=120`
4. **Use proxy** jika tersedia di corporate network
5. **Monitor cache**: Cleanup cache lama secara berkala
6. **Version pinning**: Specify exact driver version untuk reproducibility

### Example: Complete Corporate Setup
```java
// Corporate-ready configuration
import io.github.bonigarcia.wdm.WebDriverManager;

public class CorporateSetup {
    public static void setupChromeDriver() {
        WebDriverManager wdm = WebDriverManager.chromedriver();
        
        // If have internal mirror
        // wdm.driverRepositoryUrl(new URL("https://internal.mirror.com/"));
        
        // If have proxy
        wdm.proxy("proxy.company.com:8080");
        wdm.proxyUser("username");
        wdm.proxyPass("password");
        
        // Custom cache location
        wdm.cachePath("/opt/selenium-drivers");
        
        // Increase timeout for slow network
        wdm.timeout(120);
        
        // Pin specific version for reproducibility
        wdm.driverVersion("131.0.6778.85");
        
        wdm.setup();
    }
}
```

---

## Troubleshooting

### Error: "HTTP 403" or "Connection timeout"
- Pastikan proxy settings benar
- Whitelist DNS di firewall
- Gunakan pre-downloaded drivers

### Error: "Driver not found in cache"
- Clear cache: `rm -rf ~/.cache/selenium/`
- Re-download dengan `WebDriverManager.chromedriver().clearDriverCache().setup()`

### Error: "Version mismatch"
- Pin specific versions di code
- Update cache secara berkala
- Use `wdm.avoidBrowserDetection=true` untuk force latest

---

## Summary

**Untuk corporate CI/CD**, best approach adalah:
1. ✅ Pre-download chromedriver ke artifact storage internal
2. ✅ Configure Katalan dengan local driver path
3. ✅ Setup cache di CI/CD pipeline
4. ✅ Whitelist minimal DNS yang diperlukan

Dengan approach ini, **tidak perlu download berulang** dan **lebih cepat** execution di CI/CD! 🚀
