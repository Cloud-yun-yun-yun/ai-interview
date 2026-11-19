FROM ubuntu:22.04
WORKDIR /app

# 声明环境变量（指向 docker-compose 挂载进来的目录）
ENV JAVA_HOME=/opt/java
ENV PATH=$JAVA_HOME/bin:$PATH
ENV TZ=Asia/Shanghai

EXPOSE 8080

# 使用绝对路径启动，确保即使 PATH 没生效也能找到 java
ENTRYPOINT ["/opt/java/bin/java", "-XX:+UseContainerSupport", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]