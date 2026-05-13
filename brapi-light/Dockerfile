FROM python:3.12-slim

WORKDIR /app

# Install uv
COPY --from=ghcr.io/astral-sh/uv:0.6.17 /uv /usr/local/bin/uv

# Install dependencies (cache mount for faster rebuilds)
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-dev

# Copy source
COPY brapi_light/ brapi_light/

# Ensure database directory exists
RUN mkdir -p /data

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD python -c "from urllib.request import urlopen; urlopen('http://localhost:8000/brapi/v2/serverinfo')"

CMD ["uv", "run", "--no-sync", "uvicorn", "brapi_light.main:app", "--host", "0.0.0.0", "--port", "8000"]
