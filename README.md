# NovaMall 星选商城

NovaMall 是一个采用 **Java Spring Boot + Vue 3** 构建的前后端分离商城项目，由原 `milk-store` 项目重新设计与重构。项目提供从商品浏览、搜索筛选、购物车到提交订单、查看订单的完整基础购物流程，适合作为全栈学习、课程设计或二次开发脚手架。

## 项目亮点

- 现代化响应式商城首页，适配桌面端与移动端
- 商品分类、关键词搜索、精选商品与实时库存展示
- 本地购物车、数量调整和订单金额计算
- 收货信息校验、库存扣减、订单创建与订单查询
- Spring Boot 统一异常响应与事务控制
- 默认使用 H2 内存数据库，克隆后无需安装数据库即可运行
- 提供 MySQL 8 配置和 Docker Compose，可切换为持久化存储
- 前后端接口通过 Vite Proxy 联调，避免本地跨域问题

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 前端 | Vue 3、Composition API、Vite、原生 CSS |
| 后端 | Java 17、Spring Boot 3、Spring Web、Spring JDBC、Bean Validation |
| 数据库 | H2（默认）、MySQL 8（可选） |
| 构建工具 | Maven、npm |

## 目录结构

```text
NovaMall/
├─ backend/                         # Spring Boot 后端
│  ├─ src/main/java/com/novamall/  # API、业务逻辑与 DTO
│  ├─ src/main/resources/           # 配置、建表和演示数据
│  ├─ src/test/                     # 后端接口测试
│  └─ pom.xml
├─ frontend/                        # Vue 3 前端
│  ├─ src/App.vue                   # 商城主界面
│  ├─ src/api.js                    # API 请求封装
│  ├─ src/styles.css                # 响应式样式
│  └─ package.json
├─ docker-compose.yml               # 可选 MySQL 服务
└─ README.md
```

## 快速开始

### 环境要求

- JDK 17 或更高版本
- Maven 3.9+
- Node.js 18+
- npm 9+

### 1. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`，H2 控制台地址为 `http://localhost:8080/h2-console`。

H2 连接信息：

```text
JDBC URL: jdbc:h2:mem:novamall
User Name: sa
Password: （留空）
```

### 2. 启动前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。

## 使用 MySQL

1. 启动 MySQL 容器：

```bash
docker compose up -d mysql
```

2. 使用 `mysql` Profile 启动后端：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

默认账号和密码均为 `root`。也可以通过环境变量覆盖：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

首次使用现有 MySQL 服务时，请执行 `backend/src/main/resources/schema-mysql.sql` 建表，并按需录入商品数据。

## API 接口

| 方法 | 地址 | 说明 |
| --- | --- | --- |
| GET | `/api/products` | 商品列表，支持 `keyword`、`category` 参数 |
| GET | `/api/categories` | 商品分类列表 |
| GET | `/api/orders` | 查询全部订单 |
| GET | `/api/orders/{orderNo}` | 根据订单号查询订单 |
| POST | `/api/orders` | 创建订单并扣减库存 |

创建订单示例：

```json
{
  "customerName": "张三",
  "phone": "13800138000",
  "address": "上海市浦东新区示例路 1 号",
  "items": [
    { "productId": 1, "quantity": 2 }
  ]
}
```

## 构建与测试

```bash
cd backend
mvn test
mvn package
```

```bash
cd frontend
npm install
npm run build
```

## 后续可扩展方向

- JWT 登录、用户中心与角色权限
- 商品和订单管理后台
- Redis 购物车与库存缓存
- 支付平台与物流接口
- 图片上传、对象存储和商品评价
- Elasticsearch 商品检索

## License

[MIT](LICENSE)
