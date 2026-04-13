# 多阶段构建 - 后端 Dockerfile

# 构建阶段
FROM maven:3.8-openjdk-17-slim AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests -B

# 运行阶段
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/gobang-server-1.0.0.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
