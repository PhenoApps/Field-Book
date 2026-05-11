FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml .
RUN pip install --no-cache-dir -e .

COPY brapi_light/ brapi_light/

EXPOSE 8000

CMD ["uvicorn", "brapi_light.main:app", "--host", "0.0.0.0", "--port", "8000"]
