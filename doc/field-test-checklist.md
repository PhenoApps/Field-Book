# Field Book 协作改造 — 功能测试清单

> **测试环境**：Android 模拟器 + brapi-light 后端
> - 后端地址：`http://10.0.2.2:8000/brapi/v2`
> - 宿主机验证：`http://localhost:8000/brapi/v2/serverinfo`
> - 同步间隔：**1分钟**（测试用）

---

## 前置准备

### 0.1 重新编译安装 APK

> 同步间隔已改为 1 分钟，需要重新 Build → Run。

```
Android Studio → Run 'app'（Ctrl+F5 或点击绿色三角）
```

### 0.2 确认后端正在运行

在宿主机终端执行：

```bash
curl http://localhost:8000/brapi/v2/serverinfo
```

预期返回：`{"result":{"serverName":"brapi-light","serverVersion":"0.1.0"...`

### 0.3 配置 BrAPI 连接

在模拟器 App 中：
1. 进入 **Settings**（设置）
2. 找到 **BrAPI** 部分
3. **Base URL** 填写：`http://10.0.2.2:8000/brapi/v2`
4. **BrAPI Version** 选择：**V2**
5. 返回主界面

---

## 一、基础连接测试

| # | 测试项 | 操作步骤 | 预期结果 | 结果 |
|---|--------|---------|---------|:---:|
| 1.1 | 导入试验 | 主界面 → Import → 选择 BrAPI → 选择 Programs/Trials/Studies | 能看到后端返回的数据列表 | |
| 1.2 | 导入性状 | 同上流程 → 选择 Traits | 能导入性状变量 | |
| 1.3 | 采集数据 | 选择一个 Study → 进入采集界面 → 录入几条观测值 | 数据保存到本地 SQLite | |

---

## 二、手动同步测试

### 2.1 上传观测数据

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 2.1.1 | 采集 3 条观测数据（数值 + 分类） | 本地数据就绪 | |
| 2.1.2 | 主界面 → **Sync** → 点击 **Upload** | 进度条完成，显示上传数量 | |
| 2.1.3 | 宿主机验证：`curl http://localhost:8000/brapi/v2/observations` | 返回刚上传的 3 条数据，每条含 `observationDbId` + `rev=1` | |

### 2.2 下载观测数据

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 2.2.1 | 宿主机用 curl 创建一条新观测（见下方命令） | 服务端有新数据 | |
| 2.2.2 | 模拟器 → **Sync** → **Download** | 新数据出现在本地 | |

**宿主机创建观测命令：**
```bash
curl -X POST http://localhost:8000/brapi/v2/observations \
  -H "Content-Type: application/json" \
  -d '[{"observationUnitDbId":"<你的unitId>","observationVariableDbId":"<你的variableId>","studyDbId":"<你的studyId>","observationVariableName":"Test","value":"999"}]'
```

### 2.3 增量同步

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 2.3.1 | 宿主机验证：`curl "http://localhost:8000/brapi/v2/sync/changes?since=2025-01-01T00:00:00Z"` | 返回所有观测，含 `lastSyncedTime` 和 `rev` | |

---

## 三、冲突检测测试

### 3.1 乐观锁冲突

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 3.1.1 | 宿主机更新某观测（替换 `<obsId>` 和 `<studyId>` 为实际值）：`curl -X PUT http://localhost:8000/brapi/v2/observations -H "Content-Type: application/json" -d '{"<obsId>":{"value":"111","rev":1}}'` | 返回 `rev=2`，更新成功 | |
| 3.1.2 | 再次用旧 rev 更新（模拟冲突）：`curl -X PUT http://localhost:8000/brapi/v2/observations -H "Content-Type: application/json" -d '{"<obsId>":{"value":"222","rev":1}}'` | 返回 `metadata.status` 中含 `conflicts`，数据不变 | |

### 3.2 模拟器冲突

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 3.2.1 | 在模拟器中修改某观测值为 "AAA" | 本地编辑完成 | |
| 3.2.2 | 宿主机把同一观测值改为 "BBB"：`curl -X PUT ... -d '{"<obsId>":{"value":"BBB","rev":<当前rev>}}'` | 服务端更新成功 | |
| 3.2.3 | 模拟器 → **Sync** → **Download** | 弹出冲突提示，显示字段级差异（Value: S=BBB vs L=AAA） | |
| 3.2.4 | 选择 **Server** 或 **Local** 解决冲突 | 冲突消除 | |

---

## 四、图片上传测试

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 4.1 | 在采集界面给某观测拍照 | 照片附件已保存 | |
| 4.2 | Sync → Upload（确认 Images 开关打开） | 上传成功 | |
| 4.3 | 宿主机验证：`curl http://localhost:8000/brapi/v2/images` | 返回图片元数据记录 | |

---

## 五、后台自动同步测试

### 5.1 自动上传

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 5.1.1 | 模拟器中新采集 2 条观测（不上传） | 本地新增数据就绪 | |
| 5.1.2 | 等待约 1-2 分钟（同步间隔=1分钟） | — | |
| 5.1.3 | 查看通知栏 | 出现 "Sync Complete: N uploaded" 通知 | |
| 5.1.4 | 宿主机验证：`curl http://localhost:8000/brapi/v2/observations` | 新数据已自动上传 | |

### 5.2 网络断开时行为

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 5.2.1 | 模拟器开启飞行模式 | — | |
| 5.2.2 | 采集 1 条观测 | 本地保存 | |
| 5.2.3 | 等待 2 分钟 | 无通知（网络不可用，Worker 未执行） | |
| 5.2.4 | 关闭飞行模式 | 网络恢复 | |
| 5.2.5 | 等待约 1-2 分钟 | 通知出现，数据自动上传 | |

---

## 六、端到端协作场景

### 6.1 三人协作（模拟）

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 6.1.1 | 宿主机创建 5 条观测（curl POST） | 服务端有数据 | |
| 6.1.2 | 模拟器 Sync → Download | 5 条数据下载到本地 | |
| 6.1.3 | 模拟器修改其中 1 条 → Upload | 上传成功，rev 递增 | |
| 6.1.4 | 宿主机修改另一条 → 模拟器 Download | 新数据可见 | |

### 6.2 数据完整性

| # | 操作步骤 | 预期结果 | 结果 |
|---|---------|---------|:---:|
| 6.2.1 | 模拟器采集 5 条观测 → Upload | 全部上传 | |
| 6.2.2 | `curl http://localhost:8000/brapi/v2/observations` | 数据条数正确 | |
| 6.2.3 | 宿主机关闭后端 → 模拟器采集 2 条 → 启动后端 → 等待自动同步 | 离线数据在上线后自动上传 | |

---

## 七、验证命令速查

```bash
# 查看服务状态
curl http://localhost:8000/brapi/v2/serverinfo | python -m json.tool

# 查看所有观测
curl http://localhost:8000/brapi/v2/observations | python -m json.tool

# 查看增量变更
curl "http://localhost:8000/brapi/v2/sync/changes?since=2025-01-01T00:00:00Z" | python -m json.tool

# 查看图片
curl http://localhost:8000/brapi/v2/images | python -m json.tool

# 查看某个 study 的观测
curl "http://localhost:8000/brapi/v2/observations?studyDbId=<studyId>" | python -m json.tool
```

---

## 问题记录

| # | 问题描述 | 复现步骤 | 严重程度 |
|---|---------|---------|:---:|
| | | | |
| | | | |
