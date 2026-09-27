# Smart Parking AI Service

FastAPI foundation for the Smart Parking AI service. AI models and inference
logic are not included yet.

## Requirements

- Python 3.12

## Create the virtual environment and install dependencies

Run these commands from `ai-service/` in PowerShell:

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

Optionally copy `.env.example` to `.env` to override local settings. Defaults
work without a `.env` file.

## Run the service

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

The health endpoint is available at `http://127.0.0.1:8001/health` and returns:

```json
{
  "status": "ok",
  "service": "smart-parking-ai-service"
}
```

## Run tests

```powershell
.\.venv\Scripts\python.exe -m pytest
```
