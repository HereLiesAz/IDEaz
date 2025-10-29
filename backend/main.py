from fastapi import FastAPI
from . import schemas

app = FastAPI()


@app.get("/")
async def root():
    return {"message": "Cortex AI Backend is running"}


@app.post("/v1/agent/execute", response_model=schemas.ExecuteResponse)
async def execute_agent(request: schemas.ExecuteRequest):
    """
    Mock endpoint for the main agent execution.
    Returns a hardcoded file modification.
    """
    print(f"Received prompt: {request.prompt}")
    mock_diff = """<<<<<<< SEARCH
    // Existing code
=======
    // New code added by AI
>>>>>>> REPLACE"""

    mock_modification = schemas.FileModification(
        file_path="app/src/main/java/com/example/MainActivity.kt",
        diff=mock_diff
    )

    return schemas.ExecuteResponse(modifications=[mock_modification])


@app.post("/v1/completion/inline", response_model=schemas.InlineCompletionResponse)
async def inline_completion(request: schemas.InlineCompletionRequest):
    """
    Mock endpoint for inline code completion.
    Returns a hardcoded completion string.
    """
    print(f"Received file content with cursor at: {request.cursor_position}")

    return schemas.InlineCompletionResponse(
        completion="    println(\"Hello, Cortex!\")\n"
    )
