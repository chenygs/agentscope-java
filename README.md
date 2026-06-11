# AgentScope Builder (sa-token edition)

原 `agentscope-builder` 的重做版本：
- Spring Boot 4 + WebFlux
- sa-token 鉴权
- 砍掉所有 IM 渠道
- 5 大工厂（Agent/Model/Tool/Skill/Hook）

设计文档见 `agentscope-java/docs/superpowers/specs/2026-06-11-agentscope-builder-saton-design.md`。

## 本地启动

```bash
mvn spring-boot:run
```

服务监听 8080。默认账号 `admin/admin`，首次登录后请改密。

## 登录示例

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
# {"token":"...","userId":"admin","username":"admin"}

curl http://localhost:8080/api/auth/me \
  -H "satoken: <token from previous step>"
# {"userId":"admin","username":"admin"}
```

## Maven 本地仓库

由于使用了 `D:\PROGRAM\maven\Repository` 作为本地仓库，请确保 `~/.m2/settings.xml` 已配好 `localRepository` 节。

## 数据库

默认 H2 file 模式，数据落在 `./data/builderdb.mv.db`。要切 MySQL/PG，下个里程碑会加 profile `jdbc`。
