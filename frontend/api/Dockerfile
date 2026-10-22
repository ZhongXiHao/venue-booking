# ---------- 构建阶段：Maven + JDK ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只复制 pom.xml 下载依赖，利用层缓存：源码变了依赖层不用重下
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q package -DskipTests

# ---------- 运行阶段：只有 JRE + jar ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 非 root 用户运行
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/target/*.jar app.jar
USER app

EXPOSE 8080

# 数据库连接信息全部来自环境变量（DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD），镜像里不含任何密码
# exec 形式，java 作为前台主进程（PID 1），能正确接收 SIGTERM
ENTRYPOINT ["java", "-jar", "app.jar"]
