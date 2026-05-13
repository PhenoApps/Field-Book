# Field Book 汉化术语表

Issue #1 约定的术语 + 高频 UI 词汇。翻译时必须严格遵循。

## 农业/育种核心术语

| English | 中文 | 说明 |
|---------|------|------|
| trait | 性状 | 可观测/测量的表型特征 |
| plot | 小区 | 田间实验的最小单元 |
| observation unit | 观测单元 | 数据采集的最小单位 |
| entry | 条目 | 种质在试验中的编号 |
| rep | 重复 | 同一试验的重复块 |
| study | 试验 | 一个完整的田间试验 |
| field | 田块 | 物理上的一块田地 |
| germplasm | 种质 | 遗传资源 |
| observation level | 观测层级 | 如 plot、plant |
| observation variable | 观测变量 | 等同于 trait |
| ontology | 本体 | 性状分类体系 |
| season | 季节 | 生长季 |
| location | 地点 | 试验地点 |
| program | 项目 | 育种项目 |
| trial | 试验 | 较 study 更大范围的试验 |

## 性状格式

| English | 中文 |
|---------|------|
| format | 格式 |
| numeric | 数值型 |
| categorical | 类别型 |
| date | 日期型 |
| percent | 百分数 |
| counter | 计数型 |
| disease rating | 病害评级 |
| boolean | 逻辑型 |
| text | 文本型 |
| photo | 图片 |
| audio | 音频 |
| multicategorical | 多重分类 |
| location | 位置 |
| barcode | 条形码 |
| label print | 标签打印 |
| GNSS | GNSS |
| spectral | 光谱 |

## BrAPI 同步

| English | 中文 | 说明 |
|---------|------|------|
| BrAPI | BrAPI | 保留不译 |
| base URL | 基础网址 | |
| OIDC | OIDC | 保留不译 |
| authorization | 授权 | |
| pagination | 分页 | |
| observation | 观测值 | |
| sync | 同步 | |
| server info | 服务器信息 | |

## UI 通用

| English | 中文 |
|---------|------|
| cancel | 取消 |
| OK | 同意 |
| yes / no | 是 / 否 |
| clear / close | 清除 / 关闭 |
| save / delete | 保存 / 删除 |
| import / export | 导入 / 导出 |
| settings | 设置 |
| preferences | 偏好设置 |
| profile | 说明 |
| about | 关于 |
| tutorial | 教程 |
| search | 搜索 |
| summary | 汇总 |
| lock | 锁定 |
| resources | 资源 |
| toolbar | 工具栏 |
| infobar | 信息栏 |
| datagrid | 数据表格 |
| changelog | 更改记录 |
| citation | 引用 |
| warning | 警告 |
| error | 错误 |
| permission | 权限 |

## 采集界面 (Collect)

| English | 中文 |
|---------|------|
| collect | 收集 |
| data collection | 数据采集 |
| entry | 记录 |
| next entry | 下一个记录 |
| N/A | 缺失 |
| stored | 已保存 |
| repeated measures | 重复测量 |

## 硬件/设备

| English | 中文 | 说明 |
|---------|------|------|
| barcode scanner | 条形码扫描器 | |
| zebra printer | 斑马打印机 | |
| GoPro | GoPro | 保留不译 |
| USB camera | USB 相机 | |
| InnoSpectra | InnoSpectra | 保留不译 |
| Nix | Nix | 保留不译（颜色传感器） |
| Canon | Canon | 保留不译 |
| Wear OS | Wear OS | 保留不译 |

## 田间导航 (GeoNav)

| English | 中文 | 说明 |
|---------|------|------|
| GeoNav | 田间导航 | |
| GNSS | GNSS | 保留不译 |
| log | 日志 | |

## 保留不译原则

以下类型的词永远不翻译：
- **协议/标准名**：BrAPI、OIDC、GNSS、HTTP、JSON、XML
- **品牌/产品名**：GoPro、Canon、Nix、InnoSpectra、Wear OS、Zebra
- **占位符**：`%1$s`、`%s`、`%d`
- **代码标识符**：`translatable="false"` 标记的 string
