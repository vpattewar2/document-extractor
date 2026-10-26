import { useState } from "react";
import "./App.css";

const API_URL = "http://localhost:8080/api/v1/ocr/extract";

function App() {
  const [file, setFile] = useState(null);
  const [status, setStatus] = useState("idle");
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  // Store the selected file.
  function handleFileChange(event) {
    const selectedFile = event.target.files?.[0];

    setFile(selectedFile || null);
    setResult(null);
    setError("");
    setStatus("idle");

    if (!selectedFile) return;

    // Allow only the formats supported by the backend.
    const allowedExtensions = /\.(pdf|jpe?g|png)$/i;

    if (!allowedExtensions.test(selectedFile.name)) {
      setError("Please select a PDF, JPG, JPEG, or PNG file.");
      setFile(null);
      event.target.value = "";
      return;
    }

    // Keep file size within the backend's 10 MB limit.
    if (selectedFile.size > 10 * 1024 * 1024) {
      setError("File size must not exceed 10 MB.");
      setFile(null);
      event.target.value = "";
    }
  }

  // Upload the file and display the backend's JSON response.
  async function handleUpload() {
    if (!file) {
      setError("Please select a document first.");
      return;
    }

    setStatus("processing");
    setResult(null);
    setError("");

    try {
      const formData = new FormData();

      // The field name must match the backend's MultipartFile parameter.
      formData.append("file", file);

      const response = await fetch(API_URL, {
        method: "POST",
        body: formData,
      });

      // Read the response even when the server returns an error.
      const responseText = await response.text();
      let responseData;

      try {
        responseData = JSON.parse(responseText);
      } catch {
        throw new Error(
            response.ok
                ? "The server returned an invalid JSON response."
                : `Request failed with HTTP ${response.status}.`
        );
      }

      if (!response.ok) {
        throw new Error(
            responseData.message ||
            responseData.error ||
            `Request failed with HTTP ${response.status}.`
        );
      }

      setResult(responseData);
      setStatus("success");
    } catch (err) {
      setError(
          err.message === "Failed to fetch"
              ? "Cannot connect to the backend. Make sure Spring Boot is running on port 8080 and CORS is configured."
              : err.message || "Something went wrong during OCR processing."
      );
      setStatus("error");
    }
  }

  // Copy the complete JSON response to the clipboard.
  async function handleCopy() {
    if (!result) return;

    try {
      await navigator.clipboard.writeText(
          JSON.stringify(result, null, 2)
      );
      setStatus("copied");
    } catch {
      setError("Could not copy JSON. Please select and copy it manually.");
    }
  }

  return (
      <main className="page">
        <header className="header">
          <div className="brand-icon">DOC</div>
          <div>
            <h1>Document Extractor</h1>
            <p>Upload a document and extract structured information</p>
          </div>
        </header>

        <section className="panel">
          <h2>Upload your document</h2>
          <p className="description">
            Supported formats: PDF, JPG, JPEG and PNG. Maximum size: 10 MB.
          </p>

          <label className="upload-area" htmlFor="document">
            <span className="upload-icon">↑</span>
            <strong>
              {file ? file.name : "Choose a document to upload"}
            </strong>
            <span>
            {file
                ? `${(file.size / 1024 / 1024).toFixed(2)} MB`
                : "Click here to browse your files"}
          </span>
            <input
                id="document"
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={handleFileChange}
            />
          </label>

          <button
              className="primary-button"
              onClick={handleUpload}
              disabled={!file || status === "processing"}
          >
            {status === "processing" ? "Processing document..." : "Extract data"}
          </button>

          {status === "processing" && (
              <div className="status processing">
                <span className="spinner" />
                Uploading document and extracting text with OCR...
              </div>
          )}

          {status === "success" && (
              <div className="status success">
                Document processed successfully.
              </div>
          )}

          {status === "copied" && (
              <div className="status success">
                JSON copied to clipboard.
              </div>
          )}

          {status === "error" && error && (
              <div className="status failure">{error}</div>
          )}

          {status !== "error" && error && (
              <div className="status failure">{error}</div>
          )}
        </section>

        {result !== null && (
            <section className="panel result-panel">
              <div className="result-heading">
                <div>
                  <h2>Extraction result</h2>
                  <p className="description">
                    JSON response received from the backend
                  </p>
                </div>

                <button className="copy-button" onClick={handleCopy}>
                  Copy JSON
                </button>
              </div>

              <pre className="json-output">
            {JSON.stringify(result, null, 2)}
          </pre>
            </section>
        )}

        <footer>Document Extractor · Spring Boot + React</footer>
      </main>
  );
}

export default App;