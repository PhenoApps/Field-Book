# 构建网络环境达标检查报告

> 检查日期：2026-05-12  
> 检查目标：验证 Field Book 伞形仓库各子项目的构建依赖网络可达性

---

## fieldbook-android (Gradle/Android 构建)

| 资源 | 状态 | 延迟 |
|------|------|------|
| `services.gradle.org` | ✅ 可达 | 0.73s |
| `repo1.maven.org` (Maven Central) | ✅ 可达 | 1.53s |
| `dl.google.com` (Google Maven) | ✅ 可达 | 0.31s |
| `jitpack.io` | ✅ 可达 | 2.31s |
| `raw.github.com/saki4510t` | ✅ 可达 | 1.01s |

**结论：Android 构建网络环境完全达标。**

---

## brapi-light (Python/uv)

| 资源 | 状态 | 延迟 |
|------|------|------|
| `pypi.org` | ✅ 可达 | 0.70s |

**结论：Python 依赖安装网络环境达标。**

---

## Docker 构建 (brapi-light + docker-compose)

| 资源 | 状态 | 说明 |
|------|------|------|
| `ghcr.io` (uv 二进制) | ✅ 可达 | 401 是正常认证响应，3.53s |
| **`registry-1.docker.io` (Docker Hub)** | ❌ 超时 | **GFW 封锁** |
| `registry.cn-hangzhou.aliyuncs.com` (阿里云) | ✅ 可达 | 401 认证正常 |

---

## 🔴 关键问题：Docker Hub 无法访问

### 影响范围

- `brapi-light/Dockerfile` 中的 `FROM python:3.12-slim` 无法拉取
- `docker compose up -d` 会失败

### 当前 Docker 配置状态

- `/etc/docker/daemon.json` — **不存在**，未配置任何镜像加速器
- Docker `Registry Mirrors` — **空白**

---

## 解决方案

### 方案一（推荐）：配置阿里云镜像加速器

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://registry.cn-hangzhou.aliyuncs.com"]
}
EOF
sudo systemctl restart docker
```

阿里云镜像站可达（延迟 0.24s），无需注册即可使用加速服务。

### 方案二：使用阿里云容器镜像服务（需注册）

注册阿里云账号 → 容器镜像服务 → 获取专属加速地址，稳定性更好但需额外注册步骤。

### 方案三：改用 GitHub Container Registry

`ghcr.io` 可达，但不包含官方 Python 基础镜像的同步副本，实施可行性低。

---

## 总结

| 子系统 | 评估 |
|--------|------|
| Android Gradle 构建 | ✅ 达标 |
| Python pip/uv 安装 | ✅ 达标 |
| Docker 构建 | 🔴 不达标 — Docker Hub 被封锁，需配置镜像加速器 |
