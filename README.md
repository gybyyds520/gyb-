# 基于 Ollama 大模型的高校学情预测分析系统

福州理工学院 · 计算与信息科学学院 · 计算机科学与技术
学生：郭耀彬（2301132013）　指导教师：李斌

---

## 一、文档

| 文档 | 说明 |
|---|---|
| `docs/01-竞品分析与功能增强方案.md` | 竞品调研、行业标配能力、功能增强清单 |
| `docs/02-项目实施计划.md` | 分阶段实施计划、里程碑、风险对策 |
| `docs/作业帮功能装载方案.md` | 作业帮功能盘点、装载映射、分批实施清单 |
| `docs/学习通功能装载方案.md` | 学习通功能盘点、装载映射（课程空间/签到/章节测验）、第二批待指令 |
| `docs/多端化实施方案.md` | Web / 微信小程序 / 移动端 / 桌面端四端规划、技术选型、分批实施 |
| `docs/全学段扩展计划.md` | 高校→小初高大全学段扩展计划（P0 底座→P1 中学→P2 小学→P3 多校）与四决策点 |
| `docs/市场竞品分析报告.md` | 15 家竞品（K12 7 + 高校/国际 8）深度调研、差距分析、可借鉴清单（证据分级标注） |
| `docs/完善缺陷路线图.md` | 竞品优点→缺陷完善的三步走收官：P0/P1/P2/P3 任务卡与验收口径 |
| `docs/全项目功能真伪摸排报告.md` | API 层 202/202、按钮层 32vue、e2e 8 项功能真伪摸排结论（含章节测验提交 bug 修复记录） |
| `README.md` | 系统概述、启动方式、功能一览、已知环境问题 |

## 二、目录结构

```
gybxx/
├─ docs/        论文与过程文档（含竞品/作业帮/学习通/多端化方案）
├─ sql/         schema.sql（40 张表）+ seed_base.sql（基础数据）+ schema_phase2~12.sql（增量迁移，共 48 张表）
├─ server/      Spring Boot 3.2.5 后端
├─ web/         Vue 3 + Vite 前端（Web / 移动端响应式）
├─ desktop/     Electron 桌面端（加载 web 构建产物，可打包安装包/绿色版）
├─ ml/          Python 预测模型 + FastAPI 推理服务
└─ mini/        微信小程序端（uni-app，规划中）
```

## 三、技术栈

- **后端**：Spring Boot 3.2.5 / MyBatis-Plus / Spring Security + JWT / MySQL 8 / Redis / WebSocket
- **前端**：Vue 3 / Vite / Element Plus / ECharts / Pinia / Vue Router
- **预测**：Python scikit-learn（LR / RF / XGBoost）→ FastAPI 推理微服务，Java 侧 HTTP 调用
- **大模型**：Ollama 本地部署（llama3.2 主 / qwen2.5 备），SSE 流式，支持降级

## 四、快速启动

### 1. 数据库

```bash
mysql -u root -p < sql/schema.sql
mysql -u root -p < sql/seed_base.sql
```

初始管理员：`admin` / `admin123`

### 2. 后端

```bash
cd server
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

- 健康检查：http://localhost:8080/api/health
- 接口文档：http://localhost:8080/api/swagger-ui.html

### 3. 前端

```bash
cd web
npm install
npm run dev
```

访问 http://localhost:5173

### 4. Python 预测服务

```bash
cd ml
pip install -r requirements.txt
python run.py            # 监听 http://127.0.0.1:8000
```

### 5. Ollama 大模型服务

```bash
ollama serve
ollama pull llama3.2:3b
ollama pull nomic-embed-text    # 知识库向量化
```

### 6. Windows 桌面端（Electron）

```bash
cd desktop
npm install          # 安装 electron 与 electron-builder
npm run build:web    # 以 http://127.0.0.1:8080/api 为后端地址构建桌面版前端
npm start            # 启动桌面应用
npm run pack         # 打包 NSIS 安装包 + 绿色版（release/ 目录）
```

> 桌面端复用 Web 全部功能；API 地址通过 `VITE_API_BASE` 注入，默认指向本机后端 8080。

后端通过 `xqfx.ollama.*` 配置接入；服务不可用时自动降级为规则化文本，功能不中断。

### 7. 大模型双轨策略（本地优先 · 云端可选降级）

系统采用「本地 Ollama 优先、外部 AI 云端兜底」双轨路由，回应当前高校对大模型数据安全与可用性的要求：

- **路由模式** `xqfx.llm.provider=auto`：本地优先 → 本地不可用时若开启 `xqfx.ollama.fallback-to-cloud` 则转云端 → 均不可用时规则化降级（功能不中断）。
- **云端通道** `xqfx.llm.cloud.*`（OpenAI 兼容网关，如 DeepSeek/OpenAI/通义）：`base-url` / `api-key` / `model` / `timeout`。**api-key 留空则云端通道自动关闭，行为与升级前完全一致（零风险升级）**。
- **出校脱敏** `xqfx.privacy.mask-llm-prompt=true`：发送云端前对手机号、身份证自动打码；调用日志 `ai_call_log` 记录实际 provider 来源（本地/云端/规则），可审计。
- **健康检查** `GET /ai/health` 返回 Provider 状态：`localAvailable` / `cloudConfigured` / `activeProvider`（LOCAL / CLOUD / RULE_FALLBACK）/ `providerMode`。

### 8. 预测模型训练数据源诊断

- **训练数据**：`PredictionService.buildTrainingDataset()` 用历史学期特征 + 下学期是否挂科做标签（有监督时间序列切分），LR/RF/XGB 训练与评估，冷启动由 `ConstantPredictor` 兜底。
- **诊断接口** `GET /ml/dataset-summary`：返回可训练样本数、学生数、学期跨度、风险/正常类别分布、特征覆盖率与数据状态（READY / COLD_START / IMBALANCED），用于答辩与生产环境数据健康度检查。
- 演示环境数据由 bootstrap（200 名学生）与 realtime 模拟流提供；生产环境切换 `xqfx.acad.provider` 接入真实教务数据。

## 五、当前进度

- [x] Phase 0：工程骨架 + 数据库全量设计
- [x] Phase 1：数据层与学情数据管理（CRUD / Excel 导入 / 质量校验 / 脱敏 / 演示数据生成）
- [x] Phase 2：Python 预测服务 + 预测预警引擎 + 干预闭环
- [x] Phase 3：Ollama 大模型分析服务（报告 / 建议 / RAG 流式问答）
- [x] Phase 4：学生画像标签体系与可视化分析
- [x] Phase 5：通知中心、系统管理与操作审计
- [x] Phase 6：自动化测试体系（后端 1992 + 前端 519 + ML 62 = 2573）
- [x] Phase 6.5：多维预警类型扩展（学业/出勤/作业/挂科/进度/心理/行为/消费）、成长档案时间轴、偏科分析
- [x] Phase 6.6：学习目标设定 + 资源推荐、待办中心（多角色聚合）、LLM 归因解读（预警/偏科）
- [x] Phase 6.7：教务系统对接适配器（Mock）、模型管理评估中心、学情智能体会话
- [x] Phase 6.8：校级学情总览大屏（驾驶舱）
- [x] Phase 6.9：启动实时初始化 + 实时数据模拟器 + 管理员监察中心（至高无上：12 维度全量记录 + 任意账号全景 + 删除三端账号）
- [x] Phase 6.10：本地 AI 场景扩展六件套——预警周报自动生成、AI 干预方案生成落工单、自然语言查学情（NL2Query）、成绩单 AI 评语、家长通知文案个性化、学情报告导出摘要
- [x] 市场同类产品对标与能力补齐（学业指标、SLA 时效、多维下钻、数据导出、三端角色隔离）
- [x] Phase 6.11：作业帮功能装载（错题本 + AI 学科答疑 + 作业闭环）
- [x] Phase 6.12：试卷录入（图片/PDF/Word → 本地 OCR/文本提取 → AI 生成题库）
- [x] Phase 6.13：学习通功能装载（课程空间章节/资料/讨论 + 数字码签到 + 章节测验）
- [x] Phase 6.14：多端化第一批（Web 移动端响应式 + Electron Windows 桌面端）
- [ ] Phase 6.15：多端化第二批（微信小程序 uni-app 工程）
- [ ] Phase 7：论文与答辩

### 已完成功能一览

| 模块 | 能力 |
|---|---|
| 学情数据 | 学生/成绩 CRUD、Excel 导入、数据质量校验、敏感信息脱敏、演示数据生成 |
| 学业预测 | 三模型（LR/RF/XGBoost）训练与评估、批量预测、特征重要性、预测结果入库 |
| 学业预警 | 四级预警（蓝/黄/橙/红）、规则可配置（学业/出勤/作业/挂科/进度/心理/行为/消费多维类型）、预警处理与统计 |
| 干预闭环 | 预警转工单、帮扶过程记录、效果评估与关闭 |
| 大模型分析 | 学情报告、个性化建议、SSE 流式问答（RAG + nomic-embed-text 向量检索）、调用审计 |
| 学生画像 | 五维画像（学业/投入/行为/潜力/风险）、规则自动打标、历史趋势、成长档案时间轴、偏科分析（强弱项对比） |
| 教学分析 | 试卷分析（得分率/难度/区分度/知识点掌握度）、班级学情对比、课堂表现记录与排名 |
| 学业指标 | 学分绩点 GPA、欠学分统计、成绩趋势回归斜率、绩点偏离度（Z-Score） |
| 预警治理 | 多维下钻（学院/专业/年级/性别/班级）、风险事件时间线、工单 SLA 超时升级、处置及时率统计、辅导员反馈打标 |
| 数据治理 | 全量质量校验（缺失/越界/重复/不一致）落库、健康度与分布报表 |
| 通知与治理 | 预警站内通知（分级渠道 + 关怀化文案）、未读管理、用户/角色/参数配置、操作审计日志、数据权限 |
| 三端角色隔离 | 教师端 / 学生端 / 家长端独立登录入口，功能可见性与数据范围按角色严格隔离（详见下表） |
| 数据导出 | 成绩/预警/工单/学业指标 Excel 导出，带文档化字段 Schema，支持外部建模 |
| 错题举一反三 | 试卷分析中针对错题调用本地大模型生成同类巩固练习，教师端一键生成，学生端「我的巩固练习」回看 |
| 站内通讯 | 教师 ↔ 学生 ↔ 家长 三端互发消息，通讯录按身份关系推导，越权发送拦截，新消息同步进通知中心 |
| 学习目标 | 学生设定均分/绩点/单科目标，系统实时计算当前值与差距、达成进度；教师可查看协助 |
| 资源推荐 | 基于偏科弱科与目标差距规则化生成视频/练习/辅导/冲刺推荐清单 |
| 待办中心 | 按角色聚合待办（教师：预警+工单+回执；学生：预警+目标+未读；家长：子女预警+回执） |
| LLM 归因 | 预警/偏科事件调用本地 Ollama 生成因果链解读（现象/成因/佐证/干预建议），含调用审计与降级 |
| 教务对接 | 适配器模式对接教务系统（预留接口 + Mock 实现），按类型同步学生/课程/成绩/考勤，同步留痕 |
| 模型管理 | 模型注册表 + 评估中心：多模型指标对比、特征重要性、上线/下线切换 |
| 学情智能体 | 统一会话入口：智能问答（RAG）/学情报告/改进建议/学情数据问答场景路由，SSE 流式输出，会话本地留存 |
| AI 场景扩展 | ①预警周报（聚合全校态势 AI 解读）②AI 干预方案→一键落工单 ③自然语言查学情（意图规则路由+AI 解释）④成绩单 AI 评语 ⑤家长通知个性化文案（可一键发送）⑥学情报告导出摘要（下载 .md） |
| 校级大屏 | 学情总览驾驶舱：在校规模/预警分布/趋势/学院排行/风险热力/最新动态，自动刷新与全屏 |
| 启动实时状态 | 空库自动初始化演示数据，运行期定时模拟新考勤/在线行为/新预警持续流入 |
| 监察中心 | 管理员专属（至高无上）：监察三端数据概览、12 维度全量记录、任意账号数据全景、删除任意学生/教师/家长账号（管理岗受保护） |
| 会员体系 | 学生/家长三档会员与充值开通，免费功能与会员功能明确分档，教师端不参与 |
| 课程空间 | 教师/学生按教学班进入课程空间：章节（CRUD + 自动编号）、资料（上传/下载/删除，本地存储）、讨论区（发帖/回复/点赞/教师置顶） |
| 课堂签到 | 教师发起 6 位数字码签到 → 学生输码签到 → 自动写考勤（当天去重）→ 教学班通知；教师统计应到/已到/未到名单、结束签到、补签 |
| 章节测验 | 教师按教学班组卷（单选/填空/判断/简答，可从题库选题）发布章节测验；学生在线作答，客观题自动判分（判断题对/√/T 归一化），成绩回传学情；提交按选课/班级归属校验（教学班测验仅该班学生可交） |
| 多端化 | Web 端移动响应式（<768px 侧栏转抽屉、卡片流）+ Windows 桌面端（Electron 加载 Web 产物，可打包安装包/绿色版）；微信小程序端规划中（uni-app） |

### 三端角色与可见范围

系统提供**教师端 / 学生端 / 家长端**三个登录入口，登录时必须选择入口，
后端校验账号实际身份与入口一致；功能可见性由 `RolePermissionService`
统一定义，同时用于后端接口鉴权与前端菜单渲染，两端口径一致。

**端内菜单分组**（按功能作用归类，前端 `layout/index.vue` 按端渲染结构与标题）

| 端 | 顶级入口 | 分组 |
|---|---|---|
| 教师/管理端 | 学情总览、校级大屏 | 学情数据（学生/成绩/试卷/班级/质量/演示）、学情分析（画像/AI/智能体/模型）、预测预警（预测/预警/干预/治理/指标）、协同沟通（通知/通讯/待办/目标）、系统管理（教务/系统/监察） |
| 学生端 | 我的学情、会员中心 | 学习助手（学情智能体/学习目标/待办中心）、消息中心（通知/站内通讯） |
| 家长端 | 家校协同、会员中心 | 消息中心（通知/站内通讯/待办中心） |

| 功能 | 系统管理员 | 校级管理者 | 院级管理者 | 辅导员 | 任课教师 | 学生 | 家长 |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| 学情总览（全校） | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| 学生名单 / 成绩管理 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| 学业预测 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| 学业预警（列表与处置） | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| 干预闭环（工单） | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| AI 学情分析 / 学生画像 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| 班级学情 / 试卷分析 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| 学业指标（GPA/欠学分/趋势） | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| 预警治理（下钻/SLA） | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| 数据质量 / 演示数据 / 系统管理 | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **我的学情**（本人成绩/指标/预警/巩固练习） | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **站内通讯**（可用对象由通讯录关系决定） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **会员中心 / 会员功能** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| **家校协同**（子女概况/回执） | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| 通知中心（仅本人） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

**数据范围（行级隔离）**

| 角色 | 可见学生 |
|---|---|
| 系统管理员 / 校级管理者 | 全校 |
| 院级管理者 | 本学院 |
| 辅导员 | 所带行政班 |
| 任课教师 | 选修本人所授课程的学生 + 所带行政班 |
| 学生 | 仅本人（接口不接受外部 studentId） |
| 家长 | 仅 `student_parent` 中已绑定的子女 |

**隔离实现方式**

- 入口校验：登录时比对 `loginType` 与账号 `user_type`，不匹配返回 403
- 接口鉴权：`@RequireRole` 注解（支持类级与方法级）+ AOP 切面（限定 controller 包），未命中角色直接 403
- 数据范围：`DataScopeService#visibleStudentIds / visibleClazzIds / requireStudentAccess`，学生、成绩、课堂表现、预警列表与统计、预测结果、干预工单、画像、导出等所有含学生数据的查询统一按登录角色限定范围（教师仅见所带班级与所授课程学生）
- 越权防护：学生端/家长端接口不接受外部 `studentId`；通知接口忽略传入 userId，仅返回本人
- 前端隔离：菜单由后端按角色下发，路由 `meta.code` 守卫拦截地址栏越权访问

**隔离检查（该隔离的隔离、该联通的联通）**

- 已隔离：学生管理 / 成绩管理（教师+管理，含数据范围）、AI 报告与建议、AI 管理配置（提示词模板 / 知识库 / 调用日志）、预警、预测、课堂表现、画像、干预工单、数据导出（教师+管理，导出按数据范围过滤）
- 基础数据字典（学院/专业/班级/学期/课程/教师/开课）读取三端共享（联通）；其新增/修改/删除为教务维护，仅教师+管理可操作
- 保持联通：健康检查（`/ai/health`、`/prediction/health`）、通知中心、站内通讯、待办中心、学习目标（教师+学生）、会员中心（学生/家长）、家长 AI 报告等跨端协作功能
- 实时数据模拟器按「学生+日期」幂等写入在线行为（当日已有则累计更新），避免重启后唯一键冲突

**演示账号**（初始密码 `123456`，由 `POST /sys/accounts/init` 批量开通）

| 端 | 账号示例 | 说明 |
|---|---|---|
| 教师端 | `admin / admin123`、`T001`~`T012` | T001 院级管理者、T002~T003 辅导员、其余任课教师 |
| 学生端 | `2300000001`（学号） | 共 50 个在校生账号 |
| 家长端 | `2300000001P1`（学号 + P1） | 每名学生 1 名绑定家长 |

**自助注册（家长 / 学生）**

登录页提供「学生 / 家长自助注册」入口（`/register`，接口 `POST /auth/register`）。注册按学校档案核验身份：学号 + 姓名 + 手机号须与 `student` 花名册完全一致且为在读状态，核验通过才开通账号——学生注册 STUDENT 账号并回写 `student.userId`；家长注册 PARENT 账号（学号+P1）并写入 `student_parent` 绑定为该学生主监护人。已开通账号 / 已绑定家长的学生拒绝重复注册；教师与管理员不开放自助注册（账号仍由管理员开通）。

### 错题举一反三（本地大模型）

针对学生做错的题目，调用本地 Ollama 模型生成同类练习题，用于巩固薄弱知识点。

**入口**：教师端「试卷分析 → 题目明细 → 举一反三」；学生端「我的学情 → 我的巩固练习」回看。

| 环节 | 说明 |
|---|---|
| 错题判定 | 学生维度 `exam_question_score.score < exam_question.full_score`；班级维度得分率 < 60% |
| 生成依据 | 原题题干 / 选项 / 参考答案 / 知识点 / 题型 + 全班得分率 + 该生得分 |
| 模型 | `xqfx.ollama.practice-model`（默认 `llama3.1:8b`），不可用时回落主模型与备用模型 |
| 输出解析 | JSON 优先；小模型常见括号错位，故增加标记式文本（`【题干】/【选项】/【答案】/【解析】/【难度】`）兜底通道 |
| 降级 | 模型不可用或输出不可解析时，生成规则化复习任务并标记 `source_type=RULE`，**不调用云端模型** |
| 落库审计 | 结果存 `exam_practice`，调用写入 `ai_call_log`（tokens / 耗时 / 状态） |
| 可见范围 | 学生仅可见指派给本人的练习，以及本人参加过考试的试卷上的全班练习；家长端不开放 |

### 作业帮功能装载（第一批：错题本 / AI 学科答疑 / 作业闭环）

按《docs/作业帮功能装载方案.md》装载与"学情分析 + 本地 AI + 三端协同"契合的闭环能力，不复制题库与内容生产。方案文档含作业帮全量功能盘点与装载映射表。

**① 错题本**（`mistake_book` 表，`/mistake` 接口）
- 错题自动收录：作业客观题答错自动入本（`source_type=HOMEWORK`），考试错题联动沿用；支持学生手动收录
- 学生端「我的学情 → 我的错题本」按课程分组查看，标注错因（概念不清/粗心/方法不当/未掌握）、可标记掌握/删除
- 教师端「学生画像/学生管理 → 查看学生错题」按数据范围校验
- 与「举一反三」联动：未掌握错题可生成同类巩固练习

**② AI 学科答疑**（`POST /ai/tutor`，本地 Ollama 非流式）
- 学生/家长/教师通用：输入题目 + 学科（可选），本地大模型按学段语态分步讲解（思路 → 步骤 → 答案）
- 学生端「我的学情 → AI 学科答疑」；调用记录写入 `ai_call_log`（bizType=TUTOR）供管理端审计
- 模型不可用时返回明确错误提示（不静默降级）

**③ 作业闭环**（`homework` + `homework_submission` 表，`/homework` 接口）
- 教师端新增「作业管理」菜单（data 分组）：按班级布置作业（单选/填空/简答作文三类题、分值、截止时间、知识点）、完成度跟踪（应交/已交/完成率/平均分）、提交明细批改
- 学生端「我的作业」：待办/逾期/已交状态，在线作答后**客观题（CHOICE/FILL）自动批改**，简答作文题由教师人工或 **AI 批改**（本地大模型评分 + 评语，写入反馈）
- 家长端「子女作业动态」：绑定子女的作业完成情况与得分反馈，家校同步
- 预警联动：作业完成度/正确率数据可供后续行为预警指标引用（第二批规划）

**装载位置汇总**：教师端 `web/src/views/HomeworkView.vue`（路由 `/homework`，菜单 `HOMEWORK_TEACHER`）；学生端 `web/src/views/student/MyStudyView.vue` 新增「我的作业 / 我的错题本 / AI 学科答疑」三个板块；家长端 `web/src/views/parent/ParentView.vue` 新增「子女作业动态」板块。

**验证**：27 个单测（错题本 8 + 作业闭环 8 + 注册 11）全过；端到端实测通过——教师布置 → 学生作答 → 客观题自动批改 30 分 → AI 批改简答题 90 分 → 错题自动收录 → 家长端可见子女得分。

### 试卷录入（图片 / PDF / Word → AI 识别 → 题库）

教师端新增「试卷录入」菜单（`/scan`，data 分组）：从外部上传试卷（拍照图片、PDF 含扫描件、Word 文档），本地识别为文本 → 本地大模型结构化解析题目 → 生成试卷分析与题库，题库可编辑纠正、检索，并一键布置为作业（与作业闭环打通）。

**识别链路**（全部本地离线、免费）：PDF 文字版用 PyMuPDF 直取文本；图片与扫描版 PDF 用 **RapidOCR（onnxruntime，模型内置）** OCR；Word 用 python-docx 提取。依赖装在 `server/.ocr_venv`（独立虚拟环境，不污染系统 Python），首次 OCR 时加载内置模型（秒级）。

**生成题库**：AI 按 {题号/题型/题干/选项/答案/解析/分值/知识点} 结构化解析，题目存入 `question_bank`（独立题库，含题型 SINGLE/MULTI/FILL/JUDGE/ESSAY）；同一录入批次还生成 AI 试卷分析（难度 + 总体点评）。AI 识别结果支持逐题人工编辑纠正（题干/选项/答案/知识点/分值/难度）。

| 端点 | 说明 |
|---|---|
| `POST /scan/upload` | 上传试卷（multipart：file/title/courseId）并提取文本 |
| `POST /scan/{id}/parse` | AI 解析题目 + 生成试卷分析（重解析幂等） |
| `GET /scan/list` / `GET /scan/{id}` | 录入列表 / 详情（含题目） |
| `DELETE /scan/{id}` | 删除录入批次（级联删题） |
| `GET /bank/list` | 题库检索（课程/知识点/题型/关键词） |
| `PUT /bank/{id}` / `DELETE /bank/{id}` | 编辑 / 删除题库题目 |
| `POST /bank/to-homework` | 勾选题目一键布置作业 |

**验证**：`PaperScanServiceTest` 7 用例 + 全量回归全过；端到端实测——上传 docx 样卷 → 提取文本 → AI 解析 4 题（单选×2/填空/简答，答案正确）→ 生成分析（难度 0.7）→ 题库入库 → 一键布置作业成功（作业管理可见）。仅教师与管理员开放，学生/家长端不出现该菜单。

单次生成 1~6 道，难度由易到难递进，题目情境与原题不同；答案与解析默认折叠，学生先作答再看。

### 学习通功能装载（课程空间 / 课堂签到 / 章节测验）

按《docs/学习通功能装载方案.md》装载"在线教学空间"类能力，让项目具备学习通式的课程组织与课堂互动体验。方案文档含学习通全量功能盘点、装载映射表与第二批待指令（投票/抢答/随机点名/学习时长统计）。

**① 课程空间**（`/course-space`，菜单 `COURSE_SPACE`，教师系 + 学生端可见；家长端不开放）
- 我的课程：教师按所授教学班、学生按选课关系（`course_enrollment`，跨班选修）∪ 行政班
- 章节：`course_chapter` 表，教师新建/编辑/删除（章号自动递增），学生查看
- 资料：`course_material` 表，教师上传（存储 `upload/course/`）、学生下载、教师删除
- 讨论区：`course_discussion` + `course_discussion_reply`，发帖/回复/点赞/教师置顶

**② 课堂签到**（`/checkin`）
- 教师发起 6 位数字码签到（`course_checkin`）→ 学生输码签到 → 写 `attendance`（status=PRESENT，remark=CHECKIN:{id}）→ 教学班站内通知
- 防重复：同一学生同一教学班当天仅可签一次；教师可统计应到/已到/未到名单、结束签到、补签

**③ 章节测验**（复用作业闭环，新增教学班维度）
- `homework` 表新增 `course_offering_id` 列；教师按教学班组卷发布，支持从题库选题导入（`/bank/list` 勾选）
- 学生在线作答：单选/填空自动判分、判断（对/√/T/正确 归一化等价）、简答人工/AI 批改；得分进入作业闭环统计

| 端点 | 说明 |
|---|---|
| `GET /course-space/my-courses` | 我的课程（教师/学生按身份） |
| `GET/POST /course-space/{offeringId}/chapters`、`PUT/DELETE /course-space/chapters/{id}` | 章节管理 |
| `GET/POST /course-space/{offeringId}/materials`、`GET /course-space/materials/{id}/file`、`DELETE /course-space/materials/{id}` | 资料上传/下载/删除 |
| `GET/POST /course-space/{offeringId}/discussions`、`POST /course-space/discussions/{id}/reply|like|pin` | 讨论区 |
| `POST /checkin`、`POST /checkin/{id}/sign`、`/close`、`/manual-sign`、`GET /checkin/list|{id}/stats|student/mine` | 签到全链路 |
| `GET /homework/offering/{offeringId}`、`GET /homework/offering/mine` | 教学班测验列表（教师/学生） |

**验证**：新增单测 15 个（CourseSpace 7 + Checkin 8）+ 全量回归 **2041 用例全过**；端到端实测——教师建章节 → 发起签到（码 058382）→ 学生输码签到成功 → 重复签到返回 400（去重生效）→ 统计应到 22/已到 1 → 组章节测验（3 题 60 分）→ 学生作答满分 60 → 讨论发帖/回复/点赞 → 上传 docx 资料 → 教学班测验列表可见。教师端与学生端课程空间页面均已在浏览器实测渲染（含移动断点）。

### 多端化（Web / 微信小程序 / 移动端 / Windows 桌面端）

按《docs/多端化实施方案.md》推进，原则：本地化、免费优先；后端 REST API 天然多端复用。

- **Web 端移动响应式**：布局壳在 `<768px` 自动切换（左侧菜单转抽屉、汉堡按钮、内容单列卡片流、表格滚动兜底）；课程空间等核心页栅格已按 `xs/md` 断点适配，手机浏览器可直接使用
- **Windows 桌面端（已完成）**：`desktop/` Electron 工程加载 Web 构建产物（`VITE_API_BASE=http://127.0.0.1:8080/api` 注入），支持系统托盘常驻；`npm run pack` 可打包 NSIS 安装包 + 绿色版
- **微信小程序端（规划中）**：uni-app（Vue3 + uv-ui）独立工程，底部 TabBar（首页/课程/消息/我的）参照学习通；后端已具备 REST + JWT 可直接复用

### 站内通讯（教师 ↔ 学生 ↔ 家长）

三端共用同一套接口（功能码 `MESSAGE`），但**不是全员可聊**——可联系对象由身份关系推导，越权发送直接拦截。

| 角色 | 通讯录来源 |
|---|---|
| 教师/辅导员 | 本人数据可见范围内的学生 + 这些学生的家长 |
| 学生 | 本人家长 + 班级班主任/辅导员 + 本人选修课程的任课教师 |
| 家长 | 本人子女 + 子女的班主任/辅导员/任课教师 |

| 端点 | 说明 |
|---|---|
| `GET /message/contacts` | 通讯录（含关系说明，如「张明的父亲」「张明的班主任」） |
| `GET /message/conversations` | 会话列表：最后一条消息 + 未读数 |
| `GET /message/history` | 与某人的聊天记录（双向、时间升序） |
| `POST /message/send` | 发送，接收人必须在通讯录内 |
| `POST /message/read` | 将与某人的会话标为已读 |
| `GET /message/unread` | 未读总数 |

消息落库 `im_message`，发送后同步推送一条站内通知（`bizType=MESSAGE`），接收人不在线也能看到提醒。页面 8 秒轮询拉取新消息。

### 会员体系（学生 / 家长）

教师端与各类管理员不参与会员体系，仅学生与家长可充值开通。

**分档原则**：涉及学生切身利益与教育公平的基础功能一律免费，只对增值分析类功能收费。

| 档位 | 价格 | 功能 |
|---|---|---|
| 普通用户（免费） | — | 我的学情概览、成绩查询、**学业预警查看**、站内通讯、通知中心、子女学业概况、家长回执 |
| VIP1 进阶会员 | 30 元/月 · 298 元/年 | 成绩趋势与多学期对比、薄弱知识点诊断、举一反三巩固练习 |
| VIP2 专业会员 | 68 元/月 · 668 元/年 | 含 VIP1 全部权益、AI 学情解读报告（本地大模型）、学业走势预测与建议 |
| VIP3 尊享会员 | 128 元/月 · 1288 元/年 | 含 VIP2 全部权益、学习报告导出、多子女对比分析 |

高等级自动包含低等级全部权益；到期后自动降为普通用户，续费按原到期日顺延。

| 端点 | 说明 |
|---|---|
| `GET /vip/levels` / `GET /vip/features` | 等级价格、免费与会员功能对照表 |
| `GET /vip/my` / `GET /vip/orders` | 我的会员（等级/到期/剩余天数）、充值记录 |
| `POST /vip/order` / `POST /vip/pay` | 下单、支付开通 |
| `GET /portal/score-trend` | 成绩趋势（VIP1，学生端） |
| `GET /parent/ai-report` | AI 学情解读（VIP2，家长端，本地大模型生成，失败降级规则解读） |

实现方式：新增 `@RequireVip` 注解 + AOP 切面，与角色鉴权相互独立（先过角色、再过会员）；等级不足返回 **402**，教职工在校验内直接放行。

> **支付说明（重要）**：当前为**模拟支付**（`payMethod=MOCK`，点击确认即开通），不含真实资金流。
> 生产环境接入真实收款需具备相应资质，并改为**以支付渠道异步回调为准**再开通，不能信任前端回传的支付结果；
> 同时需提供订单退款、到期提醒、自动续费与扣款协议等合规能力。

## 六、数据库表清单（48 张）

| 分组 | 表 |
|---|---|
| 系统与安全(6) | sys_user, sys_role, sys_user_role, sys_menu, sys_role_menu, sys_config |
| 组织与基础(8) | college, major, clazz, term, student, teacher, course, course_offering |
| 学情数据(4) | score, attendance, class_performance, online_behavior |
| 考试命题(3) | exam_paper, exam_question, exam_question_score |
| 学生画像(3) | tag_definition, student_tag, profile_snapshot |
| 预测模型(3) | ml_model, prediction_result, feature_record |
| 预警干预(4) | warning_rule, warning_record, intervention_ticket, intervention_log |
| 消息与治理(9) | notify_message, audit_log, import_task, data_quality_report, ai_prompt_template, ai_call_log, report_record, knowledge_doc, knowledge_chunk |
| 家长协同(2) | student_parent, parent_feedback |
| 巩固练习(1) | exam_practice |
| 即时通讯(1) | im_message |
| 会员体系(3) | vip_level, vip_order, user_vip |
| 学习目标(1) | learning_goal |
| 学习通装载(6) | course_chapter, course_material, course_discussion, course_discussion_reply, course_checkin, course_enrollment（homework 新增 course_offering_id 列） |

## 七、测试

```bash
# 后端单元测试
cd server && ./mvnw.cmd test

# 覆盖率报告（按需）
cd server && ./mvnw.cmd test -Pcoverage

# 前端单元测试
cd web && npm run test
```

当前用例数：**后端 2041 + 前端 519 + ML 62 = 2622**，已超过 ≥1000 的目标。

覆盖方式：边界值 + 等价类 + 参数化数据驱动（`@MethodSource` 生成组合）+ Mockito 隔离外部依赖。
重点覆盖：脱敏、数据质量、预警分级、干预流转、画像维度、标签规则、向量相似度、模板渲染、通知与数据权限、试卷统计（难度/区分度）、课堂表现、系统概览聚合。

## 八、已知环境问题

1. **JaCoCo 默认关闭**：Windows 用户名含中文时，JaCoCo 的 `-javaagent` 参数在 fork 的 JVM 中会出现路径编码错误，
   故覆盖率插件放在 `coverage` profile 中，需显式加 `-Pcoverage` 启用。
2. **Maven 未全局安装**：已提供 Maven Wrapper（`server/mvnw.cmd`），首次运行会自动下载 Maven 3.9.9，无需手动安装。
3. **Ollama 未启动**：Phase 3 前不影响启动，使用时执行 `ollama serve` 并 `ollama pull llama3.2`。
4. **Redis 未启动**：后端可正常启动（缓存相关功能暂不可用），如需使用请启动 Redis 6379。
5. **SQL 文件含 UTF-8 BOM**：直接用 `mysql < file.sql` 会报语法错误，需先去除 BOM
   （PowerShell：`[IO.File]::WriteAllText($dst, $txt.TrimStart([char]0xFEFF), New-Object Text.UTF8Encoding($false))`）。
6. **JDBC URL 编码参数**：MySQL Connector/J 不接受 `characterEncoding=utf8mb4`，需写 `utf8`。
7. **Electron 桌面端**：`desktop/` 工程加载 `web` 构建产物；首次 `npm install` 会下载 Electron 发行版（约 100MB），打包时 `electron-builder` 需联网下载 NSIS 工具链。API 地址通过 `VITE_API_BASE` 注入（默认 `http://127.0.0.1:8080/api`），后端 CORS 已放开任意来源。
