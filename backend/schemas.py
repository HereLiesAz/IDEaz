from pydantic import BaseModel
from typing import Dict, Any, List

# --- /v1/agent/execute ---

class ExecuteRequest(BaseModel):
    """
    Request model for the main agent execution endpoint.
    """
    prompt: str
    context: Dict[str, Any]  # Flexible context from the client

class FileModification(BaseModel):
    """
    Represents a single file modification action from the agent.
    """
    file_path: str
    diff: str

class ExecuteResponse(BaseModel):
    """
    Response model for the agent execution endpoint.
    For the mock service, this will be a simple list of modifications.
    """
    modifications: List[FileModification]


# --- /v1/completion/inline ---

class InlineCompletionRequest(BaseModel):
    """
    Request model for inline code completion.
    """
    file_content: str
    cursor_position: int

class InlineCompletionResponse(BaseModel):
    """
    Response model for inline code completion.
    """
    completion: str
