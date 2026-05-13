# Field Book 协作改造 — 验证清单

## 一、brapi-light 后端验证

> **可直接在当前环境执行，无需额外下载。**

### 1.1 全量测试

```bash
cd brapi-light
uv sync --extra dev        # 首次需安装依赖
uv run pytest tests/ -q    # 预期: 53 passed
uv run ruff check .        # 预期: All checks passed!
```

### 1.2 Docker 构建与启动

```bash
cd brapi-light
docker build -t brapi-light:latest .

# 启动容器（端口 38000，与 BreedBase 一致）
docker run -d --name brapi-test -p 38000:8000 \
  -e DATABASE_URL=sqlite+aiosqlite:////data/brapi.db \
  brapi-light:latest

# 验证服务发现
curl http://localhost:38000/brapi/v2/serverinfo
# 预期: {"result":{"serverName":"brapi-light",...calls:[11个端点]}}
```

### 1.3 端到端 API 测试

```bash
# 创建观测数据
curl -X POST http://localhost:38000/brapi/v2/observations \
  -H "Content-Type: application/json" \
  -d '[{"observationUnitDbId":"u1","observationVariableDbId":"v1",
        "studyDbId":"s1","observationVariableName":"Height","value":"150"}]'
# 预期: 返回 observationDbId + rev=1 + lastSyncedTime

# 乐观锁更新（应成功）
curl -X PUT http://localhost:38000/brapi/v2/observations \
  -H "Content-Type: application/json" \
  -d '{"<上一步返回的id>":{"value":"200","rev":1}}'
# 预期: 返回 rev=2

# 乐观锁冲突（应失败）
curl -X PUT http://localhost:38000/brapi/v2/observations \
  -H "Content-Type: application/json" \
  -d '{"<同上id>":{"value":"999","rev":1}}'
# 预期: metadata.status 中含 "conflicts", result.data 为空

# 增量同步
curl "http://localhost:38000/brapi/v2/sync/changes?since=2020-01-01T00:00:00Z"
# 预期: 返回 rev=2 的观测记录

# 获取程序列表
curl http://localhost:38000/brapi/v2/programs
# 预期: 200 + 分页元数据

# 上传图片元数据
curl -X POST http://localhost:38000/brapi/v2/images \
  -H "Content-Type: application/json" \
  -d '[{"observationUnitDbId":"u1","imageFileName":"photo.jpg",
        "mimeType":"image/jpeg","imageFileSize":1024}]'
# 预期: 返回 imageDbId

# 上传图片内容
curl -X PUT http://localhost:38000/brapi/v2/images/<imageDbId>/imagecontent \
  -H "Content-Type: application/octet-stream" \
  --data-binary @photo.jpg
# 预期: 200, 内容存入 SQLite BLOB

# 清理
docker rm -f brapi-test
```

### 1.4 Docker Compose 部署

```bash
cd docker
docker compose up -d
# 预期: brapi-light 在 0.0.0.0:38000 运行

curl http://localhost:38000/brapi/v2/serverinfo

# 验证数据持久化
docker compose down && docker compose up -d
curl http://localhost:38000/brapi/v2/observations
# 预期: 之前的数据仍在 (data/ 目录挂载)
```

---

## 二、Android APK 编译

> **需要在网络通畅的电脑上执行。** 当前环境无法访问 `storage.googleapis.com` 导致 R8 组件下载超时。

### 2.1 环境准备

- Android Studio Hedgehog (2024.1+) 或仅 JDK 17 + Gradle 8.x
- 网络能访问: `dl.google.com`, `repo.maven.apache.org`, `storage.googleapis.com`

### 2.2 编译步骤

```bash
cd fieldbook-android

# 确认 WorkManager 依赖已添加 (app/build.gradle 第230行附近)
grep "work-runtime-ktx" app/build.gradle
# 预期: implementation "androidx.work:work-runtime-ktx:2.10.0"

# 编译 Debug APK
./gradlew app:assembleDebug
# 预期: BUILD SUCCESSFUL

# 输出位置
ls app/build/outputs/apk/debug/app-debug.apk

# Lint 检查
./gradlew app:lint
```

### 2.3 安装到手机

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# 或直接通过 USB 传输 APK 文件安装
```

---

## 三、手机端验证

### 3.1 连接配置

```
前置条件: 手机与 brapi-light 服务器在同一局域网

1. 打开 Field Book → Settings → BrAPI
2. Base URL: http://<服务器IP>:38000/brapi/v2
3. BrAPI Version: V2
4. 保存, 返回主界面
   → 预期: 可导入试验 (Trials) 和性状 (Traits)
```

### 3.2 手动同步 — 上传

```
1. 选择一个 Study, 采集 5 条观测数据 (数值 + 分类)
2. 主界面 → Sync 按钮 → Upload
   → 预期: 进度条完成, 显示 "N uploaded"
3. 服务端验证:
   curl http://<服务器IP>:38000/brapi/v2/observations?studyDbId=<studyId>
   → 预期: 返回刚上传的 5 条数据
```

### 3.3 图片上传

```
1. 在观测条目中点击相机图标, 拍摄一张照片
2. Sync → Upload (确认 Images 开关已打开)
   → 预期: 上传成功
3. 服务端验证:
   curl http://<服务器IP>:38000/brapi/v2/images
   → 预期: 返回图片元数据记录
```

### 3.4 手动同步 — 下载

```
1. 用 curl 在服务端创建几条新观测 (模拟另一用户)
2. 手机 Sync → Download
   → 预期: 新数据出现在本地
```

### 3.5 冲突检测（需两台手机）

```
准备: 手机A + 手机B 连接同一服务器, 下载同一 Study

1. 断开两台手机的网络
2. A 离线修改观测 X 的值为 "100"
3. B 离线修改同一观测 X 的值为 "200"
4. A 连网 → Sync → Upload (成功)
5. B 连网 → Sync → Download
   → 预期: B 看到冲突提示框
   → 显示 "Value: S=100 vs L=200" (字段级红字差异)
   → 选择 Local/Server 完成解决
```

### 3.6 字段级合并

```
准备: 同上的两台手机

1. A 离线修改观测 Y 的 Value 为 "150"
2. B 离线修改同一观测 Y 的 Collector 为 "张三"
3. A 上传 → B 下载
   → 预期: 不产生冲突 (修改了不同字段)
   → B 本地自动合并: Value=150 + Collector=张三
```

### 3.7 多人协作（3人）

```
1. 3台设备连接同一服务器, 导入同一 Study
2. 各自采集 10 条不同小区的观测
3. 依次上传 → 最后一人下载
   → 预期: 30条数据全部可见, 无丢失
```

### 3.8 图片 + 观测混合

```
1. 采集 5 条观测, 其中 3 条带照片
2. 上传 → 删除本地数据 → 下载
   → 预期: 5条观测恢复, 3条图片元数据恢复
```

---

## 四、Docker 生产部署

```bash
# 在服务器上
cd docker
docker compose up -d

# 验证
docker ps | grep brapi-light
# 预期: STATUS 显示 "(healthy)"

# 开机自启 (已在 docker-compose.yml 中配置 restart: unless-stopped)
```

---

## 五、测试数据汇总

| 维度 | 数量 |
|------|:---:|
| brapi-light 单元/集成测试 | 53 |
| 覆盖 API 端点 | 15 |
| E2E 协作场景 | 4 |
| Docker 镜像大小 | ~73MB (压缩) |
| 启动时间 | <2 秒 |
| 数据库 | SQLite 单文件 |
