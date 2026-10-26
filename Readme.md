# OCR Document Extractor

A full-stack OCR application built using React, Vite, Java, and Spring Boot.
It allows users to upload PAN and Aadhaar documents in image or PDF format and extract text and structured information using OCR.

## Features

- Upload PAN and Aadhaar documents.
- Support for JPG, JPEG, PNG, and PDF files.
- OCR-based document text extraction.
- Structured extraction of document details.
- REST API built using Spring Boot.
- React-based user interface powered by Vite.
- Input validation and error handling.

## Technology Stack and Versions

The following versions were used in the development environment or specified in the project configuration.

| Technology | Version |
| Java JDK | 17.0.20 |
| Gradle | 9.8.2 |
| Spring Boot | 4.1.2 |
| Spring Dependency Management | 1.1.7 |
| Tess4J | 5.13.0 |
| Tesseract OCR | 5.5.3 |
| Apache PDFBox | 3.0.8 |
| React | 19.3.1 |
| React DOM | 19.3.1 |
| Vite | 8.3.4 |
| Node.js | 24.15.0 |
| npm | 11.12.1 |

*Development environment:* macOS on Apple Silicon.

## Prerequisites

Install the following software:

- Java JDK 17
- Node.js and npm
- Tesseract OCR
- Git, if cloning the repository

Verify below installations using commands:

java -version
./gradlew --version
node --version
npm --version
tesseract --version

## Setup and Run Instructions

### Step 1: Clone or open the project

git clone <repository-url>
cd document-extractor

### Step 2: Configure Tesseract OCR

This application uses Tess4J to integrate Tesseract OCR with Java.
On macOS, install Tesseract using Homebrew if it is not already installed using below command:

brew install tesseract

Verify the installation using below commands:
which tesseract
tesseract --version

The development configuration uses the following Tesseract language-data directory:
/opt/homebrew/share/tessdata

In `src/main/resources/application.properties`, the configured setting is:
TESSDATA_PREFIX=/opt/homebrew/share/tessdata

This path is specific to the macOS Homebrew environment. Update it for your operating system and installation.

*Note:* Ensure the backend actually reads this property and passes the value to Tesseract as required.
Spring Boot properties are not automatically exported as operating-system environment variables.

### Step 3: Build the Spring Boot application

From the project root, run:
./gradlew clean build

This command cleans previous build outputs, compiles the backend, runs the configured tests, and packages the application.

Wait for the build to complete successfully before proceeding.
On Windows, use:
gradlew.bat clean build


### Step 4: Start the Spring Boot backend
From the project root, run:
./gradlew bootRun

The backend starts on the configured Spring Boot port,`8080` default for this application.
Keep this terminal running.

### Step 5: Install and start the React frontend
Open a second terminal and navigate to the frontend directory:

cd frontend
npm install
npm run dev

Vite displays the frontend development URL in the terminal. It is default:
http://localhost:5173

Open the displayed URL in your browser.
Keep both the backend and frontend running while using the application.
Here on ui upload files get json response of aadhaar and pan card. Pdf only single page is supported.

## API Usage

### Extract document details

*Endpoint*

```http
POST /api/v1/ocr/extract
```

*Request content type:* `multipart/form-data`
*File parameter:* `file`

Supported formats:

- JPG
- JPEG
- PNG
- PDF

### Test the API using cURL

With the backend running, execute:

curl --location 'http://localhost:8080/api/v1/ocr/extract' \
--header 'Content-Type: multipart/form-data; boundary=<calculated when request is sent>' \
--form 'file=@"/Users/pattewar/Downloads/aadhar.png"'

Replace the example path with the path to an actual supported document.

The API returns a JSON response containing extracted document information. Results depend on the document type, image quality, and OCR accuracy.

## Troubleshooting
### Tesseract language data cannot be found

Verify that the configured directory exists and contains the required language-data files:
ls -la /opt/homebrew/share/tessdata


