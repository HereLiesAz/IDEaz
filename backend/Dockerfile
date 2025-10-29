# Use an official Python runtime as a parent image
FROM python:3.9-slim

# Set the working directory in the container
WORKDIR /app

# Copy the dependencies file to the working directory
COPY requirements.txt .

# Install any needed packages specified in requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application's code to the working directory
COPY . .

# Expose the port the app runs on
EXPOSE 8080

# Run uvicorn server
# The --host 0.0.0.0 is crucial for the container to be reachable from outside
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
