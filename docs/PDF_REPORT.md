# PDF Report Generation

Katalan dapat menghasilkan laporan PDF dari hasil eksekusi test otomatis berdasarkan informasi dari `execution0.log`.

## Konfigurasi

Buat file `settings/external/com.katalon.plugin.report.properties` di root project Anda:

```properties
generatePDF=true
generateHTML=true
generateCSV=false
```

### Opsi Konfigurasi

- `generatePDF` - Generate PDF report (default: `false`)
- `generateHTML` - Generate HTML report (default: `true`)
- `generateCSV` - Generate CSV report (default: `false`)

## Cara Menggunakan

### 1. Setup File Properties

Buat direktori dan file properties:

```bash
mkdir -p settings/external
cat > settings/external/com.katalon.plugin.report.properties << EOF
generatePDF=true
generateHTML=true
generateCSV=false
EOF
```

### 2. Jalankan Test

```bash
./katalan.sh run -p . -ts "Test Suites/MySuite"
```

### 3. Cek Report

Report PDF akan otomatis dihasilkan di:
```
Reports/<timestamp>/<TestSuiteName>/<timestamp>/test-report.pdf
```

Contoh:
```
Reports/20260428_153000/MySuite/20260428_153000/
├── 20260428_153000.html        # HTML report
├── test-report.pdf              # PDF report (NEW!)
├── execution0.log               # Execution log
├── JUnit_Report.xml            # JUnit XML
└── execution.properties        # Execution metadata
```

## Isi Report PDF

Report PDF mencakup:

### 1. Summary Section
- Total Tests
- Passed Tests
- Failed Tests
- Error Tests
- Total Duration
- Pass Rate (%)

### 2. Test Case Details
Untuk setiap test case:
- Test Case ID/Name
- Status (PASSED/FAILED/ERROR) dengan warna
- Duration
- Error Message (jika ada)
- Steps (max 10 step pertama)

## Contoh Penggunaan di CI/CD

### GitLab CI

```yaml
test:
  script:
    # Setup report settings
    - mkdir -p settings/external
    - echo "generatePDF=true" > settings/external/com.katalon.plugin.report.properties
    - echo "generateHTML=true" >> settings/external/com.katalon.plugin.report.properties
    - echo "generateCSV=false" >> settings/external/com.katalon.plugin.report.properties
    
    # Run tests
    - ./katalan.sh run -ts "Test Suites/RegressionTest" --headless
    
  artifacts:
    when: always
    paths:
      - Reports/**/*.pdf
      - Reports/**/*.html
    expire_in: 30 days
```

### Jenkins

```groovy
pipeline {
    agent any
    
    stages {
        stage('Setup') {
            steps {
                sh '''
                    mkdir -p settings/external
                    cat > settings/external/com.katalon.plugin.report.properties << EOF
generatePDF=true
generateHTML=true
generateCSV=false
EOF
                '''
            }
        }
        
        stage('Test') {
            steps {
                sh './katalan.sh run -ts "Test Suites/RegressionTest" --headless'
            }
        }
    }
    
    post {
        always {
            archiveArtifacts artifacts: 'Reports/**/*.pdf, Reports/**/*.html', 
                           fingerprint: true
            publishHTML([
                reportDir: 'Reports',
                reportFiles: '**/*.html',
                reportName: 'Test Report'
            ])
        }
    }
}
```

### Azure DevOps

```yaml
steps:
  - script: |
      mkdir -p settings/external
      echo "generatePDF=true" > settings/external/com.katalon.plugin.report.properties
      echo "generateHTML=true" >> settings/external/com.katalon.plugin.report.properties
      echo "generateCSV=false" >> settings/external/com.katalon.plugin.report.properties
    displayName: 'Setup Report Configuration'

  - script: |
      ./katalan.sh run -ts "Test Suites/RegressionTest" --headless
    displayName: 'Run Tests'

  - task: PublishPipelineArtifact@1
    condition: always()
    inputs:
      targetPath: 'Reports'
      artifactName: 'TestReports'
```

## Troubleshooting

### PDF tidak dihasilkan

1. **Cek file properties ada dan valid:**
   ```bash
   cat settings/external/com.katalon.plugin.report.properties
   ```

2. **Cek generatePDF=true:**
   Pastikan nilai adalah `true` (lowercase)

3. **Cek execution0.log ada:**
   PDF generator membaca dari `execution0.log`. Pastikan file ini ada di direktori report.

4. **Cek log error:**
   ```bash
   # Jalankan dengan verbose
   ./katalan.sh run -ts "Test Suites/MySuite" --verbose
   ```

### PDF corrupt atau error

1. **Cek dependency iText:**
   Pastikan Maven build berhasil dan iText library ter-include

2. **Cek format execution0.log:**
   File harus berupa XML valid dengan format Katalon

3. **Cek memory:**
   Untuk test suite besar, mungkin perlu tambah heap:
   ```bash
   export JAVA_OPTS="-Xmx2g"
   ./katalan.sh run -ts "Test Suites/LargeSuite"
   ```

## Fitur Mendatang

- [ ] Custom PDF template
- [ ] Screenshot embedding di PDF
- [ ] Chart/graph visualisasi
- [ ] Multiple format dalam satu run (PDF + Excel)
- [ ] Email PDF report otomatis
- [ ] PDF dengan branding/logo custom

## Technical Details

### Library yang Digunakan
- **iText 7.2.5** - PDF generation library
- XML parsing dari `execution0.log`

### Performance
- Small suite (< 10 tests): < 1 second
- Medium suite (10-50 tests): 1-3 seconds
- Large suite (> 50 tests): 3-10 seconds

### File Size
- Rata-rata: 50-200 KB per report
- Tergantung jumlah test dan length error messages
