# brapi-light

轻量 BrAPI v2 后端，替换 BreedBase 用于田间表型数据协作采集。

## 技术栈

- **Python 3.11+** / FastAPI / SQLAlchemy / SQLite
- Pydantic v2 数据模型
- pytest + httpx 测试

## 快速开始

```bash
uv sync
uv run uvicorn brapi_light.main:app --reload --host 0.0.0.0 --port 8000
```

## BrAPI 端点

需实现的 13 个端点详见 `../doc/brapi-deploy-plan.md`。

## 测试

```bash
uv run pytest
```

遵循 BDD + TDD 双循环：先写 Gherkin 行为规格，经确认后进入红-绿-重构 TDD 循环。
